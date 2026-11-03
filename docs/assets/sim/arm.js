// Arm simulator: same structure as the chapter 8 Arm (Motion Profile + PID +
// kV on the motor controller, gravity feedforward from the robot code).
import {
  Buttons, Chart, Command, Commands, Motors, PIDController, Scheduler, SimLoop, Subsystem,
  TrapezoidProfile, Trigger, button, clamp, createLayout, el, robotControls,
  schedulerPanel, section, slider, toggle,
} from "./core.js";
import { COLORS, THREE, box, createScene, cylinder } from "./scene.js";

const DEG = Math.PI / 180;
const ROT = 2 * Math.PI;

const ARM = {
  gearRatio: 60,
  length: 0.6, // m
  mass: 4, // kg
  stowedDegrees: -30,
  maxDegrees: 110,
  friction: 0.3, // N·m·s/rad
};
ARM.comDistance = ARM.length / 2;
ARM.inertia = (ARM.mass * ARM.length * ARM.length) / 3;

// Starting gains, in the same units as the chapter 8 CTRE constants:
// volts per arm rotation, volts per (arm rotation / s), rotations / s.
const PRESETS = {
  full: { kP: 20, kD: 0.5, kG: 0.9, kV: 7.5, cruise: 0.5, accel: 1.0, profile: true, gravity: true },
  pOnly: { kP: 20, kD: 0, kG: 0, kV: 0, cruise: 0.5, accel: 1.0, profile: false, gravity: false },
  pAndG: { kP: 20, kD: 0.5, kG: 0.9, kV: 0, cruise: 0.5, accel: 1.0, profile: false, gravity: true },
};

function createArmSim(root) {
  const layout = createLayout(root);
  const motor = Motors.NEO(1);
  const gains = { ...PRESETS.full, brake: true };

  // ---------------- physics state ----------------
  const physics = {
    angle: ARM.stowedDegrees * DEG, // rad, horizontal = 0
    velocity: 0, // rad/s
    volts: 0,
    current: 0,
    disturbance: 0, // N·m
    disturbanceUntil: 0,
  };

  // ---------------- motor controller (runs every 1 ms) ----------------
  const controller = {
    mode: "neutral", // "neutral" | "throttle" | "position"
    throttle: 0,
    goal: 0, // arm rotations, stowed = 0
    arbFF: 0,
    profileState: { position: 0, velocity: 0 },
    pid: new PIDController(),
  };
  const toEncoderRotations = (degrees) => (degrees - ARM.stowedDegrees) / 360;
  const measuredRotations = () => (physics.angle / DEG - ARM.stowedDegrees) / 360;

  function controllerStep(dt) {
    if (!scheduler.enabled || controller.mode === "neutral") return 0;
    if (controller.mode === "throttle") return controller.throttle * 12;

    const profile = new TrapezoidProfile(gains.cruise, gains.accel);
    controller.profileState = gains.profile
      ? profile.step(controller.profileState, controller.goal, dt)
      : { position: controller.goal, velocity: 0 };
    const pid = controller.pid;
    pid.kP = gains.kP;
    pid.kD = gains.kD;
    const volts =
      pid.calculate(measuredRotations(), controller.profileState.position, dt) +
      gains.kV * controller.profileState.velocity +
      controller.arbFF;
    return clamp(volts, -12, 12);
  }

  // ---------------- robot code (runs every 20 ms) ----------------
  const arm = new Subsystem("Arm");
  const gravityVolts = () => (gains.gravity ? gains.kG * Math.cos(physics.angle) : 0);
  const isAtAngle = (degrees) => Math.abs(physics.angle / DEG - degrees) < 3;

  function goToAngleCommand(degrees) {
    const target = clamp(degrees, ARM.stowedDegrees, ARM.maxDegrees);
    return new Command({
      name: `ArmToAngle(${target}°)`,
      requirements: [arm],
      initialize: () => {
        // Like Motion Magic / MAXMotion: the profile starts from where the arm is now.
        controller.mode = "position";
        controller.goal = toEncoderRotations(target);
        controller.profileState = { position: measuredRotations(), velocity: physics.velocity / ROT };
        controller.pid.reset();
      },
      execute: () => {
        controller.arbFF = gravityVolts();
      },
    });
  }

  const buttons = new Buttons(["faceUp", "faceRight", "faceLeft"]);
  const input = { stick: 0 };
  const manualCommand = () =>
    Commands.runEnd(
      arm,
      () => {
        controller.mode = "throttle";
        controller.throttle = -input.stick * 0.3;
      },
      () => { controller.throttle = 0; },
      "ArmManual",
    );

  const scheduler = new Scheduler();
  scheduler.register(arm);
  scheduler.setDefaultCommand(arm, manualCommand());
  scheduler.addTrigger(new Trigger(() => buttons.get("faceUp"))).onTrue(() => goToAngleCommand(90));
  scheduler.addTrigger(new Trigger(() => buttons.get("faceRight"))).onTrue(() => goToAngleCommand(0));
  scheduler.addTrigger(new Trigger(() => buttons.get("faceLeft"))).onTrue(() => goToAngleCommand(ARM.stowedDegrees));
  scheduler.addTrigger(new Trigger(() => Math.abs(input.stick) > 0.2)).onTrue(manualCommand);

  // ---------------- physics step ----------------
  function physicsStep(dt, time) {
    const volts = controllerStep(dt);
    physics.volts = volts;
    const motorSpeed = physics.velocity * ARM.gearRatio;
    // Disabled in coast mode: the controller leaves the motor floating.
    const floating = !scheduler.enabled && !gains.brake;
    const motorTorque = floating ? 0 : motor.torque(volts, motorSpeed) * ARM.gearRatio;
    physics.current = floating ? 0 : motor.current(volts, motorSpeed);
    const gravity = -ARM.mass * 9.81 * ARM.comDistance * Math.cos(physics.angle);
    const disturbance = time < physics.disturbanceUntil ? physics.disturbance : 0;
    const accel = (motorTorque + gravity + disturbance - ARM.friction * physics.velocity) / ARM.inertia;
    physics.velocity += accel * dt;
    physics.angle += physics.velocity * dt;

    // Hard stops
    const lo = ARM.stowedDegrees * DEG;
    const hi = ARM.maxDegrees * DEG;
    if (physics.angle < lo) { physics.angle = lo; physics.velocity = Math.max(0, physics.velocity); }
    if (physics.angle > hi) { physics.angle = hi; physics.velocity = Math.min(0, physics.velocity); }
  }

  // ---------------- 3D view ----------------
  const view = createScene(layout.view, { cameraPosition: [1.6, 1.1, 1.9], target: [0, 0.55, 0] });
  const pivotHeight = 0.8;
  let armPivot;
  let ghostPivot;
  if (view) {
    const { scene } = view;
    const towerL = box([0.05, pivotHeight, 0.05], COLORS.frame);
    towerL.position.set(0, pivotHeight / 2, 0.07);
    const towerR = towerL.clone();
    towerR.position.z = -0.07;
    const base = box([0.4, 0.04, 0.3], COLORS.dark);
    base.position.y = 0.02;
    const axle = cylinder(0.015, 0.22, COLORS.dark);
    axle.rotation.x = Math.PI / 2;
    axle.position.y = pivotHeight;
    scene.add(base, towerL, towerR, axle);

    const makeArm = (color, opacity) => {
      const pivot = new THREE.Group();
      pivot.position.y = pivotHeight;
      const beam = box([ARM.length, 0.05, 0.05], color, { opacity });
      beam.position.x = ARM.length / 2;
      const head = box([0.12, 0.1, 0.16], color, { opacity });
      head.position.x = ARM.length;
      pivot.add(beam, head);
      scene.add(pivot);
      return pivot;
    };
    ghostPivot = makeArm(COLORS.target, 0.3);
    armPivot = makeArm(COLORS.moving, 1);

    // Horizontal reference line (0 degrees)
    const ref = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, pivotHeight, 0.12), new THREE.Vector3(0.75, pivotHeight, 0.12)]),
      new THREE.LineDashedMaterial({ color: 0x888888, dashSize: 0.03, gapSize: 0.02 }),
    );
    ref.computeLineDistances();
    scene.add(ref);
  }
  const overlay = el("div", "sim-overlay");
  layout.view.append(overlay);

  // ---------------- charts ----------------
  const angleChart = new Chart(layout.charts, {
    title: "手臂角度",
    unit: "度",
    min: -40,
    max: 120,
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
    series: [
      { label: "輸出", color: "#f08c2e" },
      { label: "重力補償", color: "#2e9d5b", dashed: true },
    ],
  });

  // ---------------- panel ----------------
  const loop = new SimLoop({
    robotPeriodic: () => {
      scheduler.run();
      buttons.afterLoop();
    },
    physicsStep,
    render: (time) => {
      const goalDegrees = controller.goal * 360 + ARM.stowedDegrees;
      const profileDegrees =
        controller.mode === "position" ? controller.profileState.position * 360 + ARM.stowedDegrees : physics.angle / DEG;
      if (view) {
        armPivot.rotation.z = physics.angle;
        ghostPivot.visible = controller.mode === "position";
        ghostPivot.rotation.z = profileDegrees * DEG;
        view.render();
      }
      angleChart.push(time, [physics.angle / DEG, profileDegrees, controller.mode === "position" ? goalDegrees : NaN]);
      voltChart.push(time, [physics.volts, controller.mode === "position" ? controller.arbFF : NaN]);
      angleChart.draw(time);
      voltChart.draw(time);
      overlay.textContent =
        `角度  ${(physics.angle / DEG).toFixed(1).padStart(6)}°\n` +
        `電壓  ${physics.volts.toFixed(2).padStart(6)} V\n` +
        `電流  ${Math.abs(physics.current).toFixed(1).padStart(6)} A`;
      panelScheduler.update();
    },
  });

  robotControls(layout.panel, {
    scheduler,
    loop,
    onDisable: () => { controller.mode = "neutral"; },
  });

  const keys = section(layout.panel, "手把");
  const keyRow = el("div", "sim-row");
  const hold = (name) => buttons.handlers(name);
  button(keyRow, { label: "faceUp", hint: "→ 90°", ...hold("faceUp") });
  button(keyRow, { label: "faceRight", hint: "→ 0°（水平）", ...hold("faceRight") });
  button(keyRow, { label: "faceLeft", hint: "→ 收起", ...hold("faceLeft") });
  keys.append(keyRow);
  slider(keys, { label: "右搖桿 Y", min: -1, max: 1, step: 0.05, value: 0, springBack: true, onInput: (v) => { input.stick = v; } });
  button(keys, {
    label: "往下推手臂一下",
    hint: "模擬撞到東西",
    onPress: () => {
      physics.disturbance = -25;
      physics.disturbanceUntil = loop.time + 0.15;
    },
  });

  const tuning = section(layout.panel, "控制參數");
  const sliders = {
    kP: slider(tuning, { label: "kP", min: 0, max: 60, step: 1, value: gains.kP, onInput: (v) => { gains.kP = v; } }),
    kD: slider(tuning, { label: "kD", min: 0, max: 3, step: 0.1, value: gains.kD, onInput: (v) => { gains.kD = v; } }),
    kG: slider(tuning, { label: "kG", min: 0, max: 2, step: 0.05, value: gains.kG, unit: " V", onInput: (v) => { gains.kG = v; } }),
    kV: slider(tuning, { label: "kV", min: 0, max: 12, step: 0.5, value: gains.kV, onInput: (v) => { gains.kV = v; } }),
    cruise: slider(tuning, { label: "巡航速度", min: 0.1, max: 1.5, step: 0.05, value: gains.cruise, unit: " rps", onInput: (v) => { gains.cruise = v; } }),
    accel: slider(tuning, { label: "加速度", min: 0.2, max: 5, step: 0.1, value: gains.accel, unit: " rps²", onInput: (v) => { gains.accel = v; } }),
  };
  const toggles = {
    profile: toggle(tuning, { label: "使用 Motion Profile", value: gains.profile, onChange: (v) => { gains.profile = v; } }),
    gravity: toggle(tuning, { label: "重力補償 (kG × cos)", value: gains.gravity, onChange: (v) => { gains.gravity = v; } }),
  };
  toggle(tuning, { label: "停用時 Brake 模式", value: gains.brake, onChange: (v) => { gains.brake = v; } });

  const presetRow = el("div", "sim-row");
  const applyPreset = (preset) => {
    for (const [k, s] of Object.entries(sliders)) s.set(preset[k]);
    for (const [k, t] of Object.entries(toggles)) { t.checked = preset[k]; gains[k] = preset[k]; }
  };
  button(presetRow, { label: "只有 P", onPress: () => applyPreset(PRESETS.pOnly) });
  button(presetRow, { label: "P + kG", onPress: () => applyPreset(PRESETS.pAndG) });
  button(presetRow, { label: "完整", onPress: () => applyPreset(PRESETS.full) });
  tuning.append(presetRow);

  const panelScheduler = schedulerPanel(layout.panel, scheduler);

  loop.start();
  // Exposed for debugging in the browser console.
  root.sim = { loop, physics, controller, scheduler, gains, input, buttons };
}

for (const root of document.querySelectorAll('[data-sim="arm"]')) {
  createArmSim(root);
}
