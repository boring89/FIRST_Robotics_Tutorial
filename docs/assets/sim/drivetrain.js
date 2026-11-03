// Differential drivetrain simulator on a small practice field: arcade drive,
// TurnToAngle with PID, an encoder + gyro autonomous routine, odometry drift
// from wheel slip and side impacts, and AprilTag correction.
import {
  Buttons, Chart, Command, Commands, Motors, PIDController, Scheduler, SimLoop, Subsystem,
  Trigger, button, clamp, createLayout, el, robotControls, schedulerPanel, section,
  sequence, slider, toggle,
} from "./core.js";
import { COLORS, THREE, box, createScene, cylinder } from "./scene.js";

const DRIVE = {
  gearRatio: 8.45,
  wheelRadius: 0.0762, // m (6 inch wheels)
  trackWidth: 0.6, // m
  mass: 50, // kg
  inertia: 5, // kg·m² around the vertical axis
  wheelMass: 2.8, // kg, rotor + wheel inertia reflected to each side's tread
  traction: 270, // N, maximum grip per side
  contactStiffness: 1500, // N per (m/s) of slip
  linearDrag: 4, // N·s/m
  angularDrag: 2, // N·m·s/rad
};
const FIELD = { length: 8, width: 4 };
const START = { x: 1, y: 2, heading: 0 };
// AprilTags on the field walls: position and the direction the tag faces.
const TAGS = [
  { x: 0, y: 2, facing: 0 },
  { x: FIELD.length, y: 2, facing: Math.PI },
  { x: 4, y: 0, facing: Math.PI / 2 },
  { x: 4, y: FIELD.width, facing: -Math.PI / 2 },
];

const wrap = (angle) => Math.atan2(Math.sin(angle), Math.cos(angle));
const DEG = Math.PI / 180;

function createDrivetrainSim(root) {
  const layout = createLayout(root);
  const motor = Motors.NEO(2); // two NEOs per side
  const settings = { turnKP: 0.012, turnKD: 0.0008, autoSpeed: 0.4, vision: false, squareInputs: true };

  // ---------------- physics state ----------------
  const truth = { x: START.x, y: START.y, heading: START.heading, v: 0, omega: 0, lateral: 0 };
  const wheels = { left: 0, right: 0, leftDistance: 0, rightDistance: 0, slipping: false };
  const output = { left: 0, right: 0 }; // throttle −1..1 from the robot code

  // ---------------- sensors and odometry (robot code) ----------------
  const odometry = { x: START.x, y: START.y, lastLeft: 0, lastRight: 0 };
  const gyro = () => truth.heading; // the IMU measures the true heading
  function updateOdometry() {
    const dl = wheels.leftDistance - odometry.lastLeft;
    const dr = wheels.rightDistance - odometry.lastRight;
    odometry.lastLeft = wheels.leftDistance;
    odometry.lastRight = wheels.rightDistance;
    const ds = (dl + dr) / 2;
    odometry.x += ds * Math.cos(gyro());
    odometry.y += ds * Math.sin(gyro());
  }
  let lastVision = 0;
  function visionUpdate(time) {
    if (!settings.vision || time - lastVision < 0.1) return false;
    lastVision = time;
    const seen = TAGS.some((tag) => {
      const dx = tag.x - truth.x;
      const dy = tag.y - truth.y;
      const distance = Math.hypot(dx, dy);
      const bearing = Math.abs(wrap(Math.atan2(dy, dx) - truth.heading));
      return distance < 4.5 && bearing < 35 * DEG;
    });
    if (seen) {
      const noise = () => (Math.random() - 0.5) * 0.06;
      odometry.x += 0.3 * (truth.x + noise() - odometry.x);
      odometry.y += 0.3 * (truth.y + noise() - odometry.y);
    }
    return seen;
  }
  let seeingTag = false;
  const distanceMeters = () => (wheels.leftDistance + wheels.rightDistance) / 2;

  // ---------------- robot code ----------------
  const drivetrain = new Subsystem("Drivetrain");
  function arcadeDrive(forward, rotation, square) {
    if (square) {
      forward = Math.sign(forward) * forward * forward;
      rotation = Math.sign(rotation) * rotation * rotation;
    }
    let left = forward - rotation;
    let right = forward + rotation;
    const scale = Math.max(1, Math.abs(left), Math.abs(right));
    output.left = left / scale;
    output.right = right / scale;
  }

  const input = { leftY: 0, rightX: 0 };
  const keys = { forward: 0, turn: 0 };
  const stickForward = () => clamp(-input.leftY + keys.forward, -1, 1);
  const stickTurn = () => clamp(-input.rightX + keys.turn, -1, 1);

  const arcadeDriveCommand = (scale, name) =>
    Commands.run(drivetrain, () => arcadeDrive(stickForward() * scale, stickTurn() * scale, settings.squareInputs), name);

  function driveDistanceCommand(meters) {
    let start = 0;
    const direction = Math.sign(meters);
    return new Command({
      name: `DriveDistance(${meters} m)`,
      requirements: [drivetrain],
      initialize: () => { start = distanceMeters(); },
      execute: () => arcadeDrive(settings.autoSpeed * direction, 0, false),
      isFinished: () => Math.abs(distanceMeters() - start) >= Math.abs(meters),
      end: () => arcadeDrive(0, 0, false),
    });
  }

  function turnToAngleCommand(degrees) {
    const pid = new PIDController();
    let error = 0;
    return new Command({
      name: `TurnToAngle(${degrees}°)`,
      requirements: [drivetrain],
      initialize: () => pid.reset(),
      execute: () => {
        pid.kP = settings.turnKP;
        pid.kD = settings.turnKD;
        // enableContinuousInput(-180, 180): measure the error the short way round.
        error = wrap(degrees * DEG - gyro()) / DEG;
        // Like TURN_MAX_OUTPUT: spinning harder than this makes the wheels slip.
        const rotation = clamp(pid.calculate(-error, 0, 0.02), -0.4, 0.4);
        arcadeDrive(0, rotation, false);
      },
      isFinished: () => Math.abs(error) < 2 && Math.abs(truth.omega) < 0.2,
      end: () => arcadeDrive(0, 0, false),
    });
  }

  const squareAuto = () =>
    sequence(
      "Auto: 正方形",
      driveDistanceCommand(1.5), turnToAngleCommand(90),
      driveDistanceCommand(1.5), turnToAngleCommand(180),
      driveDistanceCommand(1.5), turnToAngleCommand(-90),
      driveDistanceCommand(1.5), turnToAngleCommand(0),
    );

  const buttons = new Buttons(["leftBumper", "dpadUp", "dpadLeft", "dpadDown", "dpadRight", "auto"]);
  const scheduler = new Scheduler();
  scheduler.register(drivetrain);
  scheduler.setDefaultCommand(drivetrain, arcadeDriveCommand(1, "ArcadeDrive"));
  scheduler.addTrigger(new Trigger(() => buttons.get("leftBumper"))).whileTrue(() => arcadeDriveCommand(0.3, "SlowDrive"));
  scheduler.addTrigger(new Trigger(() => buttons.get("dpadUp"))).onTrue(() => turnToAngleCommand(0));
  scheduler.addTrigger(new Trigger(() => buttons.get("dpadLeft"))).onTrue(() => turnToAngleCommand(90));
  scheduler.addTrigger(new Trigger(() => buttons.get("dpadDown"))).onTrue(() => turnToAngleCommand(180));
  scheduler.addTrigger(new Trigger(() => buttons.get("dpadRight"))).onTrue(() => turnToAngleCommand(-90));
  scheduler.addTrigger(new Trigger(() => buttons.get("auto"))).onTrue(squareAuto);

  // ---------------- physics (every 1 ms) ----------------
  function sidePhysics(side, groundSpeed, dt) {
    const throttle = scheduler.enabled ? output[side] : 0;
    const volts = throttle * 12;
    const motorSpeed = (wheels[side] / DRIVE.wheelRadius) * DRIVE.gearRatio;
    const motorForce = (motor.torque(volts, motorSpeed) * DRIVE.gearRatio) / DRIVE.wheelRadius;
    const slip = wheels[side] - groundSpeed;
    const raw = DRIVE.contactStiffness * slip;
    const contact = clamp(raw, -DRIVE.traction, DRIVE.traction);
    if (Math.abs(raw) > DRIVE.traction) wheels.slipping = true;
    wheels[side] += ((motorForce - contact) / DRIVE.wheelMass) * dt;
    wheels[`${side}Distance`] += wheels[side] * dt;
    return contact;
  }

  function physicsStep(dt) {
    const half = DRIVE.trackWidth / 2;
    wheels.slipping = false;
    const fl = sidePhysics("left", truth.v - truth.omega * half, dt);
    const fr = sidePhysics("right", truth.v + truth.omega * half, dt);
    truth.v += ((fl + fr - DRIVE.linearDrag * truth.v) / DRIVE.mass) * dt;
    truth.omega += (((fr - fl) * half - DRIVE.angularDrag * truth.omega) / DRIVE.inertia) * dt;
    truth.lateral *= Math.exp(-6 * dt); // the wheels scrub sideways motion away
    truth.heading = wrap(truth.heading + truth.omega * dt);
    const c = Math.cos(truth.heading);
    const s = Math.sin(truth.heading);
    truth.x += (truth.v * c - truth.lateral * s) * dt;
    truth.y += (truth.v * s + truth.lateral * c) * dt;

    // Field walls
    const r = 0.45;
    if (truth.x < r || truth.x > FIELD.length - r || truth.y < r || truth.y > FIELD.width - r) {
      truth.x = clamp(truth.x, r, FIELD.length - r);
      truth.y = clamp(truth.y, r, FIELD.width - r);
      truth.v *= 0.2;
      truth.lateral = 0;
    }
  }

  // ---------------- 3D view ----------------
  // Field coordinates (x forward, y left) → three.js (x, 0, −y)
  const view = createScene(layout.view, {
    cameraPosition: [4, 6.5, 6.5],
    target: [4, 0, -2],
    groundSize: 12,
  });
  let robot;
  let ghost;
  let wheelMeshes = [];
  let trail;
  const trailPoints = [];
  if (view) {
    const { scene } = view;
    const wallMaterial = { opacity: 0.85 };
    const walls = [
      [FIELD.length / 2, 0, FIELD.length, 0.06],
      [FIELD.length / 2, FIELD.width, FIELD.length, 0.06],
      [0, FIELD.width / 2, 0.06, FIELD.width],
      [FIELD.length, FIELD.width / 2, 0.06, FIELD.width],
    ];
    for (const [x, y, w, d] of walls) {
      const wall = box([w, 0.3, d], COLORS.frame, wallMaterial);
      wall.position.set(x, 0.15, -y);
      scene.add(wall);
    }
    for (const tag of TAGS) {
      const t = box([0.02, 0.2, 0.2], 0xffffff);
      t.position.set(tag.x + Math.cos(tag.facing) * 0.04, 0.45, -(tag.y + Math.sin(tag.facing) * 0.04));
      t.rotation.y = tag.facing;
      const inner = box([0.025, 0.12, 0.12], 0x111111);
      t.add(inner);
      scene.add(t);
    }

    const makeRobot = (color, opacity) => {
      const group = new THREE.Group();
      const chassis = box([0.7, 0.12, 0.62], COLORS.dark, { opacity });
      chassis.position.y = 0.1;
      const bumper = box([0.82, 0.13, 0.74], color, { opacity });
      bumper.position.y = 0.1;
      const nose = box([0.12, 0.05, 0.2], 0xffffff, { opacity });
      nose.position.set(0.33, 0.2, 0);
      group.add(bumper, chassis, nose);
      scene.add(group);
      return group;
    };
    ghost = makeRobot(COLORS.target, 0.35);
    robot = makeRobot(COLORS.red, 1);
    for (const [x, z] of [[0.2, 0.33], [-0.2, 0.33], [0.2, -0.33], [-0.2, -0.33]]) {
      const w = cylinder(DRIVE.wheelRadius, 0.05, 0x222222);
      w.rotation.x = Math.PI / 2;
      w.position.set(x, DRIVE.wheelRadius, z);
      robot.add(w);
      // three.js +z is the field's −y, i.e. the robot's right side.
      wheelMeshes.push({ mesh: w, side: z > 0 ? "right" : "left" });
    }
    trail = new THREE.Line(new THREE.BufferGeometry(), new THREE.LineBasicMaterial({ color: COLORS.moving }));
    scene.add(trail);
  }
  const overlay = el("div", "sim-overlay");
  layout.view.append(overlay);

  // ---------------- charts ----------------
  const speedChart = new Chart(layout.charts, {
    title: "輪速（編碼器）",
    unit: "m/s",
    min: -4,
    max: 4,
    series: [
      { label: "左", color: "#f08c2e" },
      { label: "右", color: "#3d8bfd" },
    ],
  });
  const errorChart = new Chart(layout.charts, {
    title: "Odometry 誤差",
    unit: "公尺",
    min: 0,
    max: 1,
    series: [{ label: "估計和實際位置的距離", color: "#d9434b" }],
  });

  function resetPose() {
    Object.assign(truth, { x: START.x, y: START.y, heading: START.heading, v: 0, omega: 0, lateral: 0 });
    Object.assign(wheels, { left: 0, right: 0 });
    Object.assign(odometry, { x: START.x, y: START.y, lastLeft: wheels.leftDistance, lastRight: wheels.rightDistance });
    trailPoints.length = 0;
  }

  const loop = new SimLoop({
    robotPeriodic: (time) => {
      updateOdometry();
      seeingTag = visionUpdate(time);
      scheduler.run();
      buttons.afterLoop();
    },
    physicsStep,
    render: (time) => {
      const error = Math.hypot(odometry.x - truth.x, odometry.y - truth.y);
      if (view) {
        robot.position.set(truth.x, 0, -truth.y);
        robot.rotation.y = truth.heading;
        ghost.position.set(odometry.x, 0.002, -odometry.y);
        ghost.rotation.y = gyro();
        for (const { mesh, side } of wheelMeshes) mesh.rotateY(-wheels[side] * 0.016 / DRIVE.wheelRadius);
        const last = trailPoints[trailPoints.length - 1];
        if (!last || Math.hypot(last.x - truth.x, -last.z - truth.y) > 0.05) {
          trailPoints.push(new THREE.Vector3(truth.x, 0.02, -truth.y));
          if (trailPoints.length > 600) trailPoints.shift();
          trail.geometry.setFromPoints(trailPoints);
        }
        view.render();
      }
      speedChart.push(time, [wheels.left, wheels.right]);
      errorChart.push(time, [error]);
      speedChart.draw(time);
      errorChart.draw(time);
      overlay.textContent =
        `實際    (${truth.x.toFixed(2)}, ${truth.y.toFixed(2)})  ${(truth.heading / DEG).toFixed(0)}°\n` +
        `估計    (${odometry.x.toFixed(2)}, ${odometry.y.toFixed(2)})\n` +
        `誤差    ${error.toFixed(2)} m${wheels.slipping ? "  ⚠ 輪子打滑" : ""}\n` +
        `視覺    ${settings.vision ? (seeingTag ? "看到 AprilTag" : "沒看到") : "關閉"}`;
      panelScheduler.update();
    },
  });

  // ---------------- keyboard: W/S forward, A/D turn ----------------
  root.tabIndex = 0;
  const held = new Set();
  const updateKeys = () => {
    keys.forward = (held.has("w") ? 1 : 0) - (held.has("s") ? 1 : 0);
    keys.turn = (held.has("a") ? 1 : 0) - (held.has("d") ? 1 : 0);
  };
  root.addEventListener("keydown", (e) => {
    const k = e.key.toLowerCase();
    if (["w", "a", "s", "d"].includes(k)) { held.add(k); updateKeys(); e.preventDefault(); }
    if (k === "shift") buttons.press("leftBumper");
  });
  root.addEventListener("keyup", (e) => {
    const k = e.key.toLowerCase();
    held.delete(k);
    updateKeys();
    if (k === "shift") buttons.release("leftBumper");
  });
  root.addEventListener("blur", () => { held.clear(); updateKeys(); buttons.release("leftBumper"); });

  // ---------------- panel ----------------
  robotControls(layout.panel, { scheduler, loop, onDisable: () => { output.left = 0; output.right = 0; } });

  const pad = section(layout.panel, "手把（鍵盤：點一下畫面後用 W A S D，Shift 慢速）");
  slider(pad, { label: "左搖桿 Y", min: -1, max: 1, step: 0.05, value: 0, springBack: true, onInput: (v) => { input.leftY = v; } });
  slider(pad, { label: "右搖桿 X", min: -1, max: 1, step: 0.05, value: 0, springBack: true, onInput: (v) => { input.rightX = v; } });
  const dpad = el("div", "sim-row");
  button(dpad, { label: "dpadUp", hint: "→ 0°", ...buttons.handlers("dpadUp") });
  button(dpad, { label: "dpadLeft", hint: "→ 90°", ...buttons.handlers("dpadLeft") });
  button(dpad, { label: "dpadDown", hint: "→ 180°", ...buttons.handlers("dpadDown") });
  button(dpad, { label: "dpadRight", hint: "→ −90°", ...buttons.handlers("dpadRight") });
  pad.append(dpad);
  const extra = el("div", "sim-row");
  button(extra, { label: "leftBumper", hint: "按住：慢速", ...buttons.handlers("leftBumper") });
  button(extra, { label: "自動：正方形", hint: "走 1.5 m、轉 90° × 4", ...buttons.handlers("auto") });
  pad.append(extra);

  const world = section(layout.panel, "場地");
  const worldRow = el("div", "sim-row");
  button(worldRow, { label: "從側面撞一下", onPress: () => { truth.lateral += 1.2; } });
  button(worldRow, { label: "重設位置", onPress: resetPose });
  world.append(worldRow);
  toggle(world, { label: "AprilTag 視覺修正", value: settings.vision, onChange: (v) => { settings.vision = v; } });

  const tuning = section(layout.panel, "控制參數");
  slider(tuning, { label: "轉向 kP", min: 0, max: 0.05, step: 0.001, value: settings.turnKP, onInput: (v) => { settings.turnKP = v; } });
  slider(tuning, { label: "轉向 kD", min: 0, max: 0.004, step: 0.0002, value: settings.turnKD, onInput: (v) => { settings.turnKD = v; } });
  slider(tuning, { label: "自動速度", min: 0.1, max: 1, step: 0.05, value: settings.autoSpeed, onInput: (v) => { settings.autoSpeed = v; } });
  toggle(tuning, { label: "搖桿輸入平方", value: settings.squareInputs, onChange: (v) => { settings.squareInputs = v; } });

  const panelScheduler = schedulerPanel(layout.panel, scheduler);

  loop.start();
  // Exposed for debugging in the browser console.
  root.sim = { loop, truth, odometry, wheels, output, scheduler, settings, buttons, input, resetPose };
}

for (const root of document.querySelectorAll('[data-sim="drivetrain"]')) {
  createDrivetrainSim(root);
}
