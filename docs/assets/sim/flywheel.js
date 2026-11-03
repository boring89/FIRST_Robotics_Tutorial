// Flywheel shooter simulator: open-loop vs velocity control, battery sag,
// and the chapter 6/8 shoot sequence (wait until at speed, then feed).
import {
  Buttons, Chart, Command, Commands, Motors, Scheduler, SimLoop, Subsystem, Trigger,
  button, clamp, createLayout, deadline, el, robotControls, schedulerPanel, section,
  sequence, slider, toggle, waitSeconds, waitUntil,
} from "./core.js";
import { COLORS, THREE, box, createScene, cylinder } from "./scene.js";

const RAD_PER_SEC_TO_RPM = 60 / (2 * Math.PI);

const SHOOTER = {
  inertia: 0.0025, // kg·m², flywheel + shaft
  wheelRadius: 0.0508, // m (4 inch wheels)
  viscous: 0.00005, // N·m·s/rad
  coulomb: 0.02, // N·m
  launchAngle: 55 * (Math.PI / 180),
  efficiency: 0.28, // game piece speed / wheel surface speed
  exitHeight: 0.6, // m
  shotLoad: 0.9, // N·m while a game piece is squeezed through
  shotDuration: 0.08, // s
};
const BATTERY_RESISTANCE = 0.012; // Ω, battery + wiring

/** Where a game piece lands for a given flywheel speed. */
function landingDistance(rpm) {
  const v = (rpm / RAD_PER_SEC_TO_RPM) * SHOOTER.wheelRadius * SHOOTER.efficiency;
  const vx = v * Math.cos(SHOOTER.launchAngle);
  const vy = v * Math.sin(SHOOTER.launchAngle);
  const t = (vy + Math.sqrt(vy * vy + 2 * 9.81 * SHOOTER.exitHeight)) / 9.81;
  return vx * t;
}

const PRESETS = {
  openLoop: { mode: "open", throttle: 0.64, kP: 0.001, kS: 0.1, kV: 0.0018 },
  ffOnly: { mode: "velocity", throttle: 0.64, kP: 0, kS: 0.1, kV: 0.0018 },
  full: { mode: "velocity", throttle: 0.64, kP: 0.001, kS: 0.1, kV: 0.0018 },
};

function createFlywheelSim(root) {
  const layout = createLayout(root);
  const motor = Motors.NEO_VORTEX(1);
  const settings = { ...PRESETS.full, targetRpm: 4000, tolerance: 150, batteryNominal: 12.6 };
  const targetDistance = landingDistance(settings.targetRpm);

  const physics = {
    omega: 0, // rad/s
    volts: 0,
    current: 0,
    battery: settings.batteryNominal,
    loadUntil: 0,
  };
  const rpm = () => physics.omega * RAD_PER_SEC_TO_RPM;

  // ---------------- motor controller (every 1 ms) ----------------
  const controller = { mode: "neutral", throttle: 0, targetRpm: 0 };
  function controllerVolts() {
    if (!scheduler.enabled || controller.mode === "neutral") return 0;
    if (controller.mode === "throttle") return controller.throttle * physics.battery;
    const error = controller.targetRpm - rpm();
    const volts =
      settings.kS * Math.sign(controller.targetRpm) +
      settings.kV * controller.targetRpm +
      settings.kP * error;
    return clamp(volts, -physics.battery, physics.battery);
  }

  // ---------------- robot code (every 20 ms) ----------------
  const shooter = new Subsystem("Shooter");
  const intake = new Subsystem("Intake");
  const feeder = { running: false, pieces: 1, piece: null };
  const clock = () => loop.time;

  const isAtSpeed = () =>
    settings.mode === "velocity"
      ? Math.abs(rpm() - settings.targetRpm) < settings.tolerance
      : rpm() >= settings.targetRpm - settings.tolerance;

  const spinCommand = () =>
    Commands.startEnd(
      shooter,
      () => {
        if (settings.mode === "velocity") {
          controller.mode = "velocity";
          controller.targetRpm = settings.targetRpm;
        } else {
          controller.mode = "throttle";
          controller.throttle = settings.throttle;
        }
      },
      () => { controller.mode = "neutral"; },
      "Spin",
    );

  const feedCommand = () =>
    Commands.startEnd(intake, () => { feeder.running = true; }, () => { feeder.running = false; }, "Feed")
      .withTimeout(0.5, clock);

  // Velocity control waits until the flywheel is at speed (chapter 6 / 8);
  // open loop spins for a fixed second first, like the chapter 5 version.
  const shootCommand = () =>
    deadline(
      "Shoot",
      sequence(
        "ShootSequence",
        settings.mode === "velocity"
          ? waitUntil(isAtSpeed, "WaitUntil(isAtSpeed)").withTimeout(2, clock)
          : waitSeconds(1, clock, "Wait(1s)"),
        feedCommand(),
      ),
      spinCommand(),
    );

  const loadCommand = () =>
    Commands.runOnce(intake, () => { feeder.pieces = Math.min(feeder.pieces + 1, 1); }, "Intake");

  const buttons = new Buttons(["rightBumper", "faceUp", "faceDown"]);
  const scheduler = new Scheduler();
  scheduler.register(shooter, intake);
  scheduler.addTrigger(new Trigger(() => buttons.get("rightBumper"))).onTrue(shootCommand);
  scheduler.addTrigger(new Trigger(() => buttons.get("faceUp"))).whileTrue(spinCommand);
  scheduler.addTrigger(new Trigger(() => buttons.get("faceDown"))).onTrue(loadCommand);

  // ---------------- game pieces ----------------
  const shots = [];
  const flying = [];
  function launch(time) {
    const shotRpm = rpm();
    const v = physics.omega * SHOOTER.wheelRadius * SHOOTER.efficiency;
    const distance = landingDistance(shotRpm);
    const hit = Math.abs(distance - targetDistance) < 0.3;
    flying.push({
      position: new THREE.Vector3(0.18, SHOOTER.exitHeight, 0),
      velocity: new THREE.Vector3(v * Math.cos(SHOOTER.launchAngle), v * Math.sin(SHOOTER.launchAngle), 0),
      mesh: null,
      hit,
    });
    shots.unshift({ rpm: shotRpm, distance, hit });
    physics.loadUntil = time + SHOOTER.shotDuration;
  }

  // ---------------- physics (every 1 ms) ----------------
  function physicsStep(dt, time) {
    const volts = controllerVolts();
    const current = motor.current(volts, physics.omega);
    physics.battery = settings.batteryNominal - BATTERY_RESISTANCE * Math.max(0, current);
    physics.volts = volts;
    physics.current = current;

    const motorTorque = motor.torque(volts, physics.omega);
    const friction = SHOOTER.viscous * physics.omega + SHOOTER.coulomb * Math.sign(physics.omega);
    const load = time < physics.loadUntil ? SHOOTER.shotLoad * Math.sign(physics.omega) : 0;
    physics.omega += ((motorTorque - friction - load) / SHOOTER.inertia) * dt;
    if (Math.abs(physics.omega) < 0.5 && volts === 0) physics.omega = 0;

    // Feeder pushes a loaded game piece into the flywheel.
    if (feeder.running && feeder.pieces > 0 && feeder.piece === null) feeder.piece = 0;
    if (feeder.piece !== null) {
      feeder.piece += dt / 0.15;
      if (feeder.piece >= 1) {
        feeder.piece = null;
        feeder.pieces--;
        launch(time);
      }
    }

    for (const p of flying) {
      if (p.landed) continue;
      p.velocity.y -= 9.81 * dt;
      p.position.addScaledVector(p.velocity, dt);
      if (p.position.y <= 0.09) {
        p.position.y = 0.09;
        p.landed = true;
      }
    }
  }

  // ---------------- 3D view ----------------
  const view = createScene(layout.view, {
    cameraPosition: [1.9, 1.5, 4.2],
    target: [1.9, 0.6, 0],
    groundSize: 10,
  });
  let wheels = [];
  let heldPiece;
  const pieceGeometry = new THREE.SphereGeometry(0.09, 24, 16);
  const pieceMaterial = new THREE.MeshStandardMaterial({ color: 0xf2c230, roughness: 0.6 });
  if (view) {
    const { scene } = view;
    const base = box([0.5, 0.06, 0.4], COLORS.dark);
    base.position.set(0, 0.03, 0);
    const sideA = box([0.4, 0.6, 0.03], COLORS.frame);
    sideA.position.set(0, 0.33, 0.13);
    const sideB = sideA.clone();
    sideB.position.z = -0.13;
    scene.add(base, sideA, sideB);

    for (const y of [0.52, 0.68]) {
      const wheel = new THREE.Group();
      const disc = cylinder(SHOOTER.wheelRadius, 0.2, COLORS.moving);
      disc.rotation.x = Math.PI / 2;
      const stripe = box([0.012, SHOOTER.wheelRadius * 2.02, 0.205], COLORS.dark);
      wheel.add(disc, stripe);
      wheel.position.set(0.1, y, 0);
      wheel.rotation.z = SHOOTER.launchAngle;
      scene.add(wheel);
      wheels.push(wheel);
    }

    heldPiece = new THREE.Mesh(pieceGeometry, pieceMaterial);
    heldPiece.castShadow = true;
    scene.add(heldPiece);

    // Target zone on the ground
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(0.15, 0.3, 48),
      new THREE.MeshBasicMaterial({ color: 0x2e9d5b, side: THREE.DoubleSide, transparent: true, opacity: 0.8 }),
    );
    ring.rotation.x = -Math.PI / 2;
    ring.position.set(targetDistance, 0.01, 0);
    scene.add(ring);
  }
  const overlay = el("div", "sim-overlay");
  layout.view.append(overlay);

  // ---------------- charts ----------------
  const rpmChart = new Chart(layout.charts, {
    title: "飛輪轉速",
    unit: "RPM",
    min: 0,
    max: 6000,
    series: [
      { label: "實際", color: "#f08c2e" },
      { label: "目標", color: "#9aa0a6", dashed: true },
    ],
  });
  const voltChart = new Chart(layout.charts, {
    title: "電壓",
    unit: "伏特",
    min: 0,
    max: 13,
    series: [
      { label: "馬達", color: "#f08c2e" },
      { label: "電池", color: "#3d8bfd" },
    ],
  });

  const loop = new SimLoop({
    robotPeriodic: () => {
      scheduler.run();
      buttons.afterLoop();
    },
    physicsStep,
    render: (time) => {
      if (view) {
        // Show a slowed-down spin so the stripe does not alias.
        for (const w of wheels) w.rotateZ(-physics.omega * 0.0015);
        heldPiece.visible = feeder.pieces > 0;
        const t = feeder.piece ?? 0;
        heldPiece.position.set(-0.25 + 0.35 * t, 0.42 + 0.18 * t, 0);
        for (const p of flying) {
          if (!p.mesh) {
            p.mesh = new THREE.Mesh(pieceGeometry, new THREE.MeshStandardMaterial({ color: 0xf2c230, roughness: 0.6 }));
            p.mesh.castShadow = true;
            view.scene.add(p.mesh);
          }
          p.mesh.position.copy(p.position);
          if (p.landed) p.mesh.material.color.set(p.hit ? 0x2e9d5b : 0xd9434b);
        }
        while (flying.length > 8) view.scene.remove(flying.shift().mesh);
        view.render();
      }
      const target = controller.mode === "neutral" ? NaN : settings.targetRpm;
      rpmChart.push(time, [rpm(), target]);
      voltChart.push(time, [Math.abs(physics.volts), physics.battery]);
      rpmChart.draw(time);
      voltChart.draw(time);
      overlay.textContent =
        `轉速    ${rpm().toFixed(0).padStart(5)} RPM\n` +
        `電池    ${physics.battery.toFixed(2).padStart(5)} V\n` +
        `到速    ${isAtSpeed() ? "是" : "否"}\n` +
        `裝填    ${feeder.pieces} 顆`;
      shotList.textContent = shots.length
        ? shots.slice(0, 4).map((s, i) => `${shots.length - i}. ${s.rpm.toFixed(0)} RPM → ${s.distance.toFixed(2)} m ${s.hit ? "✓" : "✗"}`).join("\n")
        : `目標距離 ${targetDistance.toFixed(2)} m（±0.3 m）`;
      panelScheduler.update();
    },
  });

  // ---------------- panel ----------------
  robotControls(layout.panel, { scheduler, loop, onDisable: () => { controller.mode = "neutral"; feeder.running = false; } });

  const keys = section(layout.panel, "手把");
  const keyRow = el("div", "sim-row");
  button(keyRow, { label: "rightBumper", hint: "發射", ...buttons.handlers("rightBumper") });
  button(keyRow, { label: "faceUp", hint: "按住：只轉", ...buttons.handlers("faceUp") });
  button(keyRow, { label: "faceDown", hint: "裝填一顆", ...buttons.handlers("faceDown") });
  keys.append(keyRow);
  const shotList = el("div", "sim-running");
  keys.append(shotList);

  const tuning = section(layout.panel, "控制參數");
  const modeToggle = toggle(tuning, {
    label: "速度控制（關閉 = 開迴路）",
    value: settings.mode === "velocity",
    onChange: (v) => { settings.mode = v ? "velocity" : "open"; },
  });
  const sliders = {
    throttle: slider(tuning, { label: "開迴路輸出", min: 0.3, max: 1, step: 0.05, value: settings.throttle, onInput: (v) => { settings.throttle = v; } }),
    kP: slider(tuning, { label: "kP", min: 0, max: 0.01, step: 0.0005, value: settings.kP, onInput: (v) => { settings.kP = v; } }),
    kS: slider(tuning, { label: "kS", min: 0, max: 0.5, step: 0.05, value: settings.kS, unit: " V", onInput: (v) => { settings.kS = v; } }),
    kV: slider(tuning, { label: "kV", min: 0, max: 0.003, step: 0.0001, value: settings.kV, onInput: (v) => { settings.kV = v; } }),
  };
  slider(tuning, { label: "電池電量", min: 11.5, max: 13, step: 0.1, value: settings.batteryNominal, unit: " V", onInput: (v) => { settings.batteryNominal = v; } });

  const presetRow = el("div", "sim-row");
  const applyPreset = (preset) => {
    for (const [k, s] of Object.entries(sliders)) s.set(preset[k]);
    settings.mode = preset.mode;
    modeToggle.checked = preset.mode === "velocity";
  };
  button(presetRow, { label: "開迴路", onPress: () => applyPreset(PRESETS.openLoop) });
  button(presetRow, { label: "只有 FF", onPress: () => applyPreset(PRESETS.ffOnly) });
  button(presetRow, { label: "FF + P", onPress: () => applyPreset(PRESETS.full) });
  tuning.append(presetRow);

  const panelScheduler = schedulerPanel(layout.panel, scheduler);

  loop.start();
  // Exposed for debugging in the browser console.
  root.sim = { loop, physics, controller, scheduler, settings, feeder, shots, buttons };
}

for (const root of document.querySelectorAll('[data-sim="flywheel"]')) {
  createFlywheelSim(root);
}
