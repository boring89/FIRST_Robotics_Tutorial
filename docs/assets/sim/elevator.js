// Elevator simulator: constant gravity (kG), Motion Profile + PID + kV on the
// motor controller, and the effect of carrying extra weight.
import {
  Buttons, Chart, Command, Commands, Motors, PIDController, Scheduler, SimLoop, Subsystem,
  TrapezoidProfile, Trigger, button, clamp, createLayout, el, robotControls, schedulerPanel,
  section, slider, toggle,
} from "./core.js";
import { COLORS, THREE, box, createScene } from "./scene.js";

const ELEVATOR = {
  gearRatio: 10,
  drumRadius: 0.0254, // m
  carriageMass: 6, // kg
  pieceMass: 2, // kg
  maxHeight: 1.2, // m
  viscous: 4, // N·s/m
};

// Volts, meters and seconds: kP in V/m, kV in V/(m/s).
const PRESETS = {
  full: { kP: 10, kD: 0.5, kG: 0.35, kV: 7.9, cruise: 1.0, accel: 3, profile: true },
  pOnly: { kP: 10, kD: 0, kG: 0, kV: 0, cruise: 1.0, accel: 3, profile: false },
  pAndG: { kP: 10, kD: 0.5, kG: 0.35, kV: 0, cruise: 1.0, accel: 3, profile: false },
};

function createElevatorSim(root) {
  const layout = createLayout(root);
  const motor = Motors.NEO(2);
  const gains = { ...PRESETS.full, carrying: false, brake: true };

  const physics = { height: 0, velocity: 0, volts: 0, current: 0 };
  const mass = () => ELEVATOR.carriageMass + (gains.carrying ? ELEVATOR.pieceMass : 0);
  // Linear speed of the carriage → motor speed (rad/s)
  const toMotorSpeed = (v) => (v / ELEVATOR.drumRadius) * ELEVATOR.gearRatio;

  // ---------------- motor controller (every 1 ms) ----------------
  const controller = {
    mode: "neutral",
    throttle: 0,
    goal: 0,
    profileState: { position: 0, velocity: 0 },
    pid: new PIDController(),
  };

  function controllerStep(dt) {
    if (!scheduler.enabled || controller.mode === "neutral") return 0;
    if (controller.mode === "throttle") return controller.throttle * 12;
    const profile = new TrapezoidProfile(gains.cruise, gains.accel);
    controller.profileState = gains.profile
      ? profile.step(controller.profileState, controller.goal, dt)
      : { position: controller.goal, velocity: 0 };
    controller.pid.kP = gains.kP;
    controller.pid.kD = gains.kD;
    const volts =
      controller.pid.calculate(physics.height, controller.profileState.position, dt) +
      gains.kV * controller.profileState.velocity +
      gains.kG;
    return clamp(volts, -12, 12);
  }

  // ---------------- robot code (every 20 ms) ----------------
  const elevator = new Subsystem("Elevator");

  function goToHeightCommand(meters) {
    const target = clamp(meters, 0, ELEVATOR.maxHeight);
    return new Command({
      name: `ElevatorTo(${target.toFixed(2)} m)`,
      requirements: [elevator],
      initialize: () => {
        controller.mode = "position";
        controller.goal = target;
        controller.profileState = { position: physics.height, velocity: physics.velocity };
        controller.pid.reset();
      },
    });
  }

  const buttons = new Buttons(["faceUp", "faceRight", "faceDown"]);
  const input = { stick: 0 };
  const manualCommand = () =>
    Commands.runEnd(
      elevator,
      () => {
        controller.mode = "throttle";
        controller.throttle = -input.stick * 0.4;
      },
      () => { controller.throttle = 0; },
      "ElevatorManual",
    );

  const scheduler = new Scheduler();
  scheduler.register(elevator);
  scheduler.setDefaultCommand(elevator, manualCommand());
  scheduler.addTrigger(new Trigger(() => buttons.get("faceUp"))).onTrue(() => goToHeightCommand(1.1));
  scheduler.addTrigger(new Trigger(() => buttons.get("faceRight"))).onTrue(() => goToHeightCommand(0.6));
  scheduler.addTrigger(new Trigger(() => buttons.get("faceDown"))).onTrue(() => goToHeightCommand(0));
  scheduler.addTrigger(new Trigger(() => Math.abs(input.stick) > 0.2)).onTrue(manualCommand);

  // ---------------- physics (every 1 ms) ----------------
  function physicsStep(dt) {
    const volts = controllerStep(dt);
    physics.volts = volts;
    const motorSpeed = toMotorSpeed(physics.velocity);
    const floating = !scheduler.enabled && !gains.brake;
    const torque = floating ? 0 : motor.torque(volts, motorSpeed);
    physics.current = floating ? 0 : motor.current(volts, motorSpeed);
    const force = (torque * ELEVATOR.gearRatio) / ELEVATOR.drumRadius;
    const accel = (force - mass() * 9.81 - ELEVATOR.viscous * physics.velocity) / mass();
    physics.velocity += accel * dt;
    physics.height += physics.velocity * dt;
    if (physics.height < 0) { physics.height = 0; physics.velocity = Math.max(0, physics.velocity); }
    if (physics.height > ELEVATOR.maxHeight) { physics.height = ELEVATOR.maxHeight; physics.velocity = Math.min(0, physics.velocity); }
  }

  // ---------------- 3D view ----------------
  const view = createScene(layout.view, { cameraPosition: [1.6, 1.3, 2.2], target: [0, 0.8, 0] });
  const baseHeight = 0.15;
  let carriage;
  let ghost;
  let piece;
  if (view) {
    const { scene } = view;
    const base = box([0.5, 0.06, 0.4], COLORS.dark);
    base.position.y = 0.03;
    scene.add(base);
    for (const z of [-0.14, 0.14]) {
      const rail = box([0.04, ELEVATOR.maxHeight + 0.35, 0.04], COLORS.frame);
      rail.position.set(0, (ELEVATOR.maxHeight + 0.35) / 2, z);
      scene.add(rail);
    }
    const top = box([0.06, 0.04, 0.34], COLORS.frame);
    top.position.y = ELEVATOR.maxHeight + 0.35;
    scene.add(top);

    carriage = new THREE.Group();
    const plate = box([0.08, 0.2, 0.3], COLORS.moving);
    const forks = box([0.3, 0.03, 0.22], COLORS.moving);
    forks.position.set(0.17, -0.085, 0);
    carriage.add(plate, forks);
    scene.add(carriage);

    ghost = box([0.08, 0.2, 0.3], COLORS.target, { opacity: 0.3 });
    scene.add(ghost);

    piece = new THREE.Mesh(
      new THREE.SphereGeometry(0.08, 24, 16),
      new THREE.MeshStandardMaterial({ color: 0xf2c230, roughness: 0.6 }),
    );
    piece.position.set(0.2, 0, 0);
    piece.castShadow = true;
    carriage.add(piece);
  }
  const overlay = el("div", "sim-overlay");
  layout.view.append(overlay);

  // ---------------- charts ----------------
  const heightChart = new Chart(layout.charts, {
    title: "高度",
    unit: "公尺",
    min: 0,
    max: 1.3,
    series: [
      { label: "實際", color: "#f08c2e" },
      { label: "Profile 目標", color: "#3d8bfd", dashed: true },
      { label: "最終目標", color: "#9aa0a6", dashed: true },
    ],
  });
  const voltChart = new Chart(layout.charts, {
    title: "馬達電壓",
    unit: "伏特",
    min: -12,
    max: 12,
    series: [{ label: "輸出", color: "#f08c2e" }],
  });

  const loop = new SimLoop({
    robotPeriodic: () => {
      scheduler.run();
      buttons.afterLoop();
    },
    physicsStep,
    render: (time) => {
      const positioning = controller.mode === "position";
      if (view) {
        carriage.position.y = baseHeight + physics.height;
        ghost.visible = positioning;
        ghost.position.y = baseHeight + controller.profileState.position;
        piece.visible = gains.carrying;
        view.render();
      }
      heightChart.push(time, [
        physics.height,
        positioning ? controller.profileState.position : NaN,
        positioning ? controller.goal : NaN,
      ]);
      voltChart.push(time, [physics.volts]);
      heightChart.draw(time);
      voltChart.draw(time);
      overlay.textContent =
        `高度  ${physics.height.toFixed(3).padStart(6)} m\n` +
        `電壓  ${physics.volts.toFixed(2).padStart(6)} V\n` +
        `電流  ${Math.abs(physics.current).toFixed(1).padStart(6)} A\n` +
        `重量  ${mass().toFixed(0).padStart(6)} kg`;
      panelScheduler.update();
    },
  });

  // ---------------- panel ----------------
  robotControls(layout.panel, { scheduler, loop, onDisable: () => { controller.mode = "neutral"; } });

  const keys = section(layout.panel, "手把");
  const keyRow = el("div", "sim-row");
  button(keyRow, { label: "faceUp", hint: "→ 1.1 m", ...buttons.handlers("faceUp") });
  button(keyRow, { label: "faceRight", hint: "→ 0.6 m", ...buttons.handlers("faceRight") });
  button(keyRow, { label: "faceDown", hint: "→ 0 m", ...buttons.handlers("faceDown") });
  keys.append(keyRow);
  slider(keys, { label: "右搖桿 Y", min: -1, max: 1, step: 0.05, value: 0, springBack: true, onInput: (v) => { input.stick = v; } });
  toggle(keys, { label: "載著遊戲物件（+2 kg）", value: gains.carrying, onChange: (v) => { gains.carrying = v; } });

  const tuning = section(layout.panel, "控制參數");
  const sliders = {
    kP: slider(tuning, { label: "kP", min: 0, max: 100, step: 1, value: gains.kP, onInput: (v) => { gains.kP = v; } }),
    kD: slider(tuning, { label: "kD", min: 0, max: 5, step: 0.1, value: gains.kD, onInput: (v) => { gains.kD = v; } }),
    kG: slider(tuning, { label: "kG", min: 0, max: 1, step: 0.01, value: gains.kG, unit: " V", onInput: (v) => { gains.kG = v; } }),
    kV: slider(tuning, { label: "kV", min: 0, max: 12, step: 0.1, value: gains.kV, onInput: (v) => { gains.kV = v; } }),
    cruise: slider(tuning, { label: "巡航速度", min: 0.1, max: 1.5, step: 0.05, value: gains.cruise, unit: " m/s", onInput: (v) => { gains.cruise = v; } }),
    accel: slider(tuning, { label: "加速度", min: 0.5, max: 10, step: 0.5, value: gains.accel, unit: " m/s²", onInput: (v) => { gains.accel = v; } }),
  };
  const profileToggle = toggle(tuning, { label: "使用 Motion Profile", value: gains.profile, onChange: (v) => { gains.profile = v; } });
  toggle(tuning, { label: "停用時 Brake 模式", value: gains.brake, onChange: (v) => { gains.brake = v; } });

  const presetRow = el("div", "sim-row");
  const applyPreset = (preset) => {
    for (const [k, s] of Object.entries(sliders)) s.set(preset[k]);
    gains.profile = preset.profile;
    profileToggle.checked = preset.profile;
  };
  button(presetRow, { label: "只有 P", onPress: () => applyPreset(PRESETS.pOnly) });
  button(presetRow, { label: "P + kG", onPress: () => applyPreset(PRESETS.pAndG) });
  button(presetRow, { label: "完整", onPress: () => applyPreset(PRESETS.full) });
  tuning.append(presetRow);

  const panelScheduler = schedulerPanel(layout.panel, scheduler);

  loop.start();
  // Exposed for debugging in the browser console.
  root.sim = { loop, physics, controller, scheduler, gains, buttons, input };
}

for (const root of document.querySelectorAll('[data-sim="elevator"]')) {
  createElevatorSim(root);
}
