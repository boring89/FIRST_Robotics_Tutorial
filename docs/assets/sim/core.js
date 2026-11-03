// Shared building blocks for the mechanism simulators: motor physics,
// controllers, a small Commands v2-style scheduler, the real-time loop,
// charts and UI widgets. Each mechanism page imports what it needs.

// ---------------------------------------------------------------------------
// Motors (same model and constants as WPILib's DCMotor)
// ---------------------------------------------------------------------------

const RPM_TO_RAD_PER_SEC = (2 * Math.PI) / 60;

function dcMotor({ stallTorque, stallCurrent, freeCurrent, freeSpeedRpm }, count = 1) {
  const R = 12 / stallCurrent;
  const freeSpeed = freeSpeedRpm * RPM_TO_RAD_PER_SEC;
  const Kv = freeSpeed / (12 - R * freeCurrent); // rad/s per volt
  const Kt = stallTorque / stallCurrent; // N·m per amp
  return {
    count,
    freeSpeed,
    /** Current (A) drawn at the given voltage and motor speed (rad/s). */
    current(volts, omega) {
      return ((volts - omega / Kv) / R) * count;
    },
    /** Torque (N·m) at the given voltage and motor speed (rad/s). */
    torque(volts, omega) {
      return Kt * this.current(volts, omega);
    },
  };
}

export const Motors = {
  NEO: (n) => dcMotor({ stallTorque: 2.6, stallCurrent: 105, freeCurrent: 1.8, freeSpeedRpm: 5676 }, n),
  NEO_VORTEX: (n) => dcMotor({ stallTorque: 3.6, stallCurrent: 211, freeCurrent: 3.6, freeSpeedRpm: 6784 }, n),
  KRAKEN_X60: (n) => dcMotor({ stallTorque: 7.09, stallCurrent: 366, freeCurrent: 2, freeSpeedRpm: 6000 }, n),
};

export const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x));

// ---------------------------------------------------------------------------
// Controllers
// ---------------------------------------------------------------------------

export class PIDController {
  constructor(kP = 0, kI = 0, kD = 0) {
    this.kP = kP;
    this.kI = kI;
    this.kD = kD;
    this.reset();
  }

  reset() {
    this.integral = 0;
    this.prevError = null;
  }

  calculate(measurement, setpoint, dt) {
    const error = setpoint - measurement;
    this.integral += error * dt;
    const derivative = this.prevError === null ? 0 : (error - this.prevError) / dt;
    this.prevError = error;
    return this.kP * error + this.kI * this.integral + this.kD * derivative;
  }
}

/**
 * Trapezoid profile stepped one dt at a time, like Motion Magic / MAXMotion:
 * accelerate toward the cruise velocity, and start braking early enough to
 * stop at the goal.
 */
export class TrapezoidProfile {
  constructor(maxVelocity, maxAcceleration) {
    this.maxVelocity = maxVelocity;
    this.maxAcceleration = maxAcceleration;
  }

  step(state, goal, dt) {
    const a = this.maxAcceleration;
    const distance = goal - state.position;
    if (Math.abs(distance) < 1e-6 && Math.abs(state.velocity) < a * dt) {
      return { position: goal, velocity: 0 };
    }
    const brakingSpeed = Math.sqrt(2 * a * Math.abs(distance));
    const target = Math.sign(distance) * Math.min(this.maxVelocity, brakingSpeed);
    const velocity = state.velocity + clamp(target - state.velocity, -a * dt, a * dt);
    let position = state.position + velocity * dt;
    // Do not step past the goal.
    if (Math.sign(goal - position) !== Math.sign(distance)) {
      return { position: goal, velocity: 0 };
    }
    return { position, velocity };
  }
}

// ---------------------------------------------------------------------------
// Commands v2-style scheduler
// ---------------------------------------------------------------------------

export class Command {
  constructor({ name, requirements = [], initialize, execute, isFinished, end, status }) {
    this.name = name;
    this.requirements = requirements;
    this.status = status ?? (() => "");
    this._initialize = initialize ?? (() => {});
    this._execute = execute ?? (() => {});
    this._isFinished = isFinished ?? (() => false);
    this._end = end ?? (() => {});
  }

  initialize() { this._initialize(); }
  execute() { this._execute(); }
  isFinished() { return this._isFinished(); }
  end(interrupted) { this._end(interrupted); }

  /** Ends the command when the condition becomes true (like `.until()`). */
  until(condition) {
    const inner = this;
    return new Command({
      name: inner.name,
      requirements: inner.requirements,
      status: () => inner.status(),
      initialize: () => inner.initialize(),
      execute: () => inner.execute(),
      isFinished: () => inner.isFinished() || condition(),
      end: (interrupted) => inner.end(interrupted),
    });
  }

  /** Ends the command after the given time (like `.withTimeout()`). */
  withTimeout(seconds, clock) {
    let start = 0;
    const inner = this;
    return new Command({
      name: inner.name,
      requirements: inner.requirements,
      status: () => inner.status(),
      initialize: () => { start = clock(); inner.initialize(); },
      execute: () => inner.execute(),
      isFinished: () => inner.isFinished() || clock() - start >= seconds,
      end: (interrupted) => inner.end(interrupted),
    });
  }
}

/** Subsystem factory helpers, mirroring `run`, `runOnce`, `startEnd`, `runEnd`. */
export const Commands = {
  run: (subsystem, action, name) =>
    new Command({ name, requirements: [subsystem], execute: action }),
  runOnce: (subsystem, action, name) =>
    new Command({ name, requirements: [subsystem], initialize: action, isFinished: () => true }),
  startEnd: (subsystem, start, end, name) =>
    new Command({ name, requirements: [subsystem], initialize: start, end }),
  runEnd: (subsystem, action, end, name) =>
    new Command({ name, requirements: [subsystem], execute: action, end }),
};

const unionRequirements = (commands) => [...new Set(commands.flatMap((c) => c.requirements))];

/** Runs commands one after another (like `Commands.sequence` / `andThen`). */
export function sequence(name, ...commands) {
  let index = 0;
  return new Command({
    name,
    requirements: unionRequirements(commands),
    status: () => (index < commands.length ? `→ ${commands[index].name}` : ""),
    initialize: () => {
      index = 0;
      commands[0].initialize();
    },
    execute: () => {
      if (index >= commands.length) return;
      const current = commands[index];
      current.execute();
      if (current.isFinished()) {
        current.end(false);
        index++;
        if (index < commands.length) commands[index].initialize();
      }
    },
    isFinished: () => index >= commands.length,
    end: (interrupted) => {
      if (interrupted && index < commands.length) commands[index].end(true);
    },
  });
}

/**
 * Runs `main` and the other commands together; ends when `main` ends and
 * interrupts the rest (like `main.deadlineFor(...others)`).
 */
export function deadline(name, main, ...others) {
  let running = [];
  return new Command({
    name,
    requirements: unionRequirements([main, ...others]),
    status: () => main.status() || `→ ${main.name}`,
    initialize: () => {
      running = [...others];
      main.initialize();
      for (const c of running) c.initialize();
    },
    execute: () => {
      main.execute();
      for (const c of [...running]) {
        c.execute();
        if (c.isFinished()) {
          c.end(false);
          running.splice(running.indexOf(c), 1);
        }
      }
    },
    isFinished: () => main.isFinished(),
    end: (interrupted) => {
      main.end(interrupted);
      for (const c of running) c.end(true);
    },
  });
}

export const waitUntil = (condition, name = "WaitUntil") =>
  new Command({ name, isFinished: condition });

export const waitSeconds = (seconds, clock, name = `Wait(${seconds}s)`) =>
  new Command({ name }).withTimeout(seconds, clock);

/**
 * Gamepad button state for the simulators. A quick click can press and
 * release inside one 20 ms loop, so a release only takes effect after the
 * scheduler has seen the press: call `afterLoop()` after `scheduler.run()`.
 */
export class Buttons {
  constructor(names) {
    this.state = Object.fromEntries(names.map((n) => [n, false]));
    this.pending = new Set();
  }
  get(name) { return this.state[name]; }
  press(name) { this.state[name] = true; this.pending.delete(name); }
  release(name) { if (this.state[name]) this.pending.add(name); }
  handlers(name) {
    return { onPress: () => this.press(name), onRelease: () => this.release(name) };
  }
  afterLoop() {
    for (const name of this.pending) this.state[name] = false;
    this.pending.clear();
  }
}

export class Subsystem {
  constructor(name) {
    this.name = name;
  }
  periodic() {}
}

export class Trigger {
  constructor(condition) {
    this.condition = condition;
    this.last = false;
    this.bindings = [];
  }
  onTrue(factory) { this.bindings.push({ type: "onTrue", factory }); return this; }
  whileTrue(factory) { this.bindings.push({ type: "whileTrue", factory }); return this; }
}

export class Scheduler {
  constructor() {
    this.subsystems = [];
    this.defaults = new Map();
    this.scheduled = [];
    this.triggers = [];
    this.listeners = [];
    this.enabled = false;
  }

  register(...subsystems) { this.subsystems.push(...subsystems); }
  setDefaultCommand(subsystem, command) { this.defaults.set(subsystem, command); }
  addTrigger(trigger) { this.triggers.push(trigger); return trigger; }
  onEvent(listener) { this.listeners.push(listener); }

  emit(type, command) {
    for (const listener of this.listeners) listener(type, command);
  }

  isScheduled(command) { return this.scheduled.includes(command); }

  schedule(command) {
    if (!this.enabled || this.isScheduled(command)) return;
    // Interrupt every command that shares a requirement (new command wins).
    for (const other of [...this.scheduled]) {
      if (other.requirements.some((r) => command.requirements.includes(r))) {
        this.cancel(other);
      }
    }
    this.scheduled.push(command);
    command.initialize();
    this.emit("schedule", command);
  }

  cancel(command) {
    const i = this.scheduled.indexOf(command);
    if (i < 0) return;
    this.scheduled.splice(i, 1);
    command.end(true);
    this.emit("interrupt", command);
  }

  cancelAll() {
    for (const command of [...this.scheduled]) this.cancel(command);
  }

  setEnabled(enabled) {
    this.enabled = enabled;
    if (!enabled) this.cancelAll();
  }

  /** One robot loop: periodic, poll triggers, run commands, start defaults. */
  run() {
    for (const s of this.subsystems) s.periodic();
    if (!this.enabled) return;

    for (const trigger of this.triggers) {
      const now = Boolean(trigger.condition());
      for (const b of trigger.bindings) {
        if (now && !trigger.last) {
          b.active = b.factory();
          this.schedule(b.active);
        } else if (!now && trigger.last && b.type === "whileTrue" && b.active) {
          this.cancel(b.active);
        }
      }
      trigger.last = now;
    }

    for (const command of [...this.scheduled]) {
      if (!this.isScheduled(command)) continue;
      command.execute();
      if (command.isFinished()) {
        this.scheduled.splice(this.scheduled.indexOf(command), 1);
        command.end(false);
        this.emit("finish", command);
      }
    }

    for (const [subsystem, command] of this.defaults) {
      const busy = this.scheduled.some((c) => c.requirements.includes(subsystem));
      if (!busy) this.schedule(command);
    }
  }
}

// ---------------------------------------------------------------------------
// Real-time loop: robot code every 20 ms, motor controllers and physics every 1 ms
// ---------------------------------------------------------------------------

export const ROBOT_PERIOD = 0.02;
export const PHYSICS_PERIOD = 0.001;

export class SimLoop {
  constructor({ robotPeriodic, physicsStep, render }) {
    this.robotPeriodic = robotPeriodic;
    this.physicsStep = physicsStep;
    this.render = render;
    this.time = 0;
    this.speed = 1;
    this.paused = false;
    this.nextRobotTime = 0;
    this.lastFrame = null;
    this.frame = this.frame.bind(this);
  }

  start() { requestAnimationFrame(this.frame); }

  /** Runs the robot code and physics for the given simulated time. */
  advance(seconds) {
    let remaining = seconds;
    while (remaining > 1e-9) {
      if (this.time >= this.nextRobotTime - 1e-9) {
        this.robotPeriodic(this.time);
        this.nextRobotTime += ROBOT_PERIOD;
      }
      this.physicsStep(PHYSICS_PERIOD, this.time);
      this.time += PHYSICS_PERIOD;
      remaining -= PHYSICS_PERIOD;
    }
  }

  frame(timestamp) {
    if (this.lastFrame !== null && !this.paused) {
      // Cap the step so a background tab does not simulate minutes at once.
      this.advance(Math.min((timestamp - this.lastFrame) / 1000, 0.1) * this.speed);
    }
    this.lastFrame = timestamp;
    this.render(this.time);
    requestAnimationFrame(this.frame);
  }
}

// ---------------------------------------------------------------------------
// Chart: scrolling line plot on a canvas
// ---------------------------------------------------------------------------

function cssVar(name, fallback) {
  const value = getComputedStyle(document.body).getPropertyValue(name).trim();
  return value || fallback;
}

export class Chart {
  constructor(container, { title, unit, series, min, max, window = 10 }) {
    this.series = series.map((s) => ({ ...s, points: [] }));
    this.min = min;
    this.max = max;
    this.window = window;
    this.unit = unit;

    const box = el("div", "sim-chart");
    const header = el("div", "sim-chart__header");
    header.append(el("span", "sim-chart__title", title));
    for (const s of this.series) {
      const key = el("span", "sim-chart__key", s.label);
      key.style.setProperty("--key-color", s.color);
      header.append(key);
    }
    this.canvas = el("canvas", "sim-chart__canvas");
    box.append(header, this.canvas);
    container.append(box);
  }

  push(time, values) {
    this.series.forEach((s, i) => {
      s.points.push([time, values[i]]);
      while (s.points.length && s.points[0][0] < time - this.window) s.points.shift();
    });
  }

  draw(time) {
    const canvas = this.canvas;
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    if (canvas.width !== w * dpr || canvas.height !== h * dpr) {
      canvas.width = w * dpr;
      canvas.height = h * dpr;
    }
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    const fg = cssVar("--md-default-fg-color--light", "#888");
    const grid = cssVar("--md-default-fg-color--lightest", "#ddd");
    const left = 44;
    const right = w - 8;
    const top = 8;
    const bottom = h - 18;
    const y = (v) => bottom - ((v - this.min) / (this.max - this.min)) * (bottom - top);
    const x = (t) => left + ((t - (time - this.window)) / this.window) * (right - left);

    ctx.font = "11px var(--md-code-font-family, monospace)";
    ctx.fillStyle = fg;
    ctx.strokeStyle = grid;
    ctx.lineWidth = 1;
    const ticks = 4;
    for (let i = 0; i <= ticks; i++) {
      const v = this.min + ((this.max - this.min) * i) / ticks;
      ctx.beginPath();
      ctx.moveTo(left, y(v));
      ctx.lineTo(right, y(v));
      ctx.stroke();
      ctx.fillText(`${Math.round(v * 10) / 10}`, 4, y(v) + 4);
    }
    ctx.fillText(`${this.unit} · 最近 ${this.window} 秒`, left, h - 4);

    ctx.lineWidth = 2;
    for (const s of this.series) {
      ctx.strokeStyle = s.color;
      ctx.setLineDash(s.dashed ? [5, 4] : []);
      ctx.beginPath();
      s.points.forEach(([t, v], i) => {
        const py = y(clamp(v, this.min, this.max));
        if (i === 0) ctx.moveTo(x(t), py);
        else ctx.lineTo(x(t), py);
      });
      ctx.stroke();
    }
    ctx.setLineDash([]);
  }
}

// ---------------------------------------------------------------------------
// UI helpers
// ---------------------------------------------------------------------------

export function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

export function section(parent, title) {
  const box = el("div", "sim-section");
  box.append(el("div", "sim-section__title", title));
  parent.append(box);
  return box;
}

/** A labelled range input; `onInput(value)` fires on every change. */
export function slider(parent, { label, min, max, step, value, unit = "", onInput, springBack = false }) {
  const row = el("label", "sim-slider");
  const name = el("span", "sim-slider__label", label);
  const input = el("input");
  input.type = "range";
  input.min = min;
  input.max = max;
  input.step = step;
  input.value = value;
  const readout = el("span", "sim-slider__value");
  const show = () => { readout.textContent = `${Number(input.value)}${unit}`; };
  input.addEventListener("input", () => { show(); onInput(Number(input.value)); });
  if (springBack) {
    const release = () => { input.value = 0; show(); onInput(0); };
    input.addEventListener("pointerup", release);
    input.addEventListener("pointercancel", release);
    input.addEventListener("blur", release);
  }
  show();
  row.append(name, input, readout);
  parent.append(row);
  return {
    input,
    set(v) { input.value = v; show(); onInput(Number(v)); },
  };
}

export function button(parent, { label, hint, onPress, onRelease, kind }) {
  const b = el("button", `sim-button${kind ? ` sim-button--${kind}` : ""}`);
  b.type = "button";
  b.append(el("span", "sim-button__label", label));
  if (hint) b.append(el("span", "sim-button__hint", hint));
  b.addEventListener("pointerdown", (e) => { e.preventDefault(); onPress?.(); });
  const up = () => onRelease?.();
  b.addEventListener("pointerup", up);
  b.addEventListener("pointerleave", up);
  b.addEventListener("click", (e) => e.preventDefault());
  parent.append(b);
  return b;
}

export function toggle(parent, { label, value, onChange }) {
  const row = el("label", "sim-toggle");
  const input = el("input");
  input.type = "checkbox";
  input.checked = value;
  input.addEventListener("change", () => onChange(input.checked));
  row.append(input, el("span", null, label));
  parent.append(row);
  return input;
}

/** Shows the commands the scheduler is running and a short event log. */
export function schedulerPanel(parent, scheduler) {
  const box = section(parent, "Command Scheduler");
  const running = el("div", "sim-running");
  const log = el("ol", "sim-log");
  box.append(running, log);
  const labels = { schedule: "開始", interrupt: "中斷", finish: "結束" };
  scheduler.onEvent((type, command) => {
    const item = el("li", `sim-log__item sim-log__item--${type}`);
    item.textContent = `${labels[type]}：${command.name}`;
    log.prepend(item);
    while (log.children.length > 6) log.lastChild.remove();
  });
  return {
    update() {
      const names = scheduler.scheduled
        .map((c) => {
          const status = c.status();
          return `${c.name}（${c.requirements.map((r) => r.name).join("、")}）${status ? `\n  ${status}` : ""}`;
        })
        .join("\n");
      running.textContent = names || "（沒有 Command 在執行）";
    },
  };
}

/** Enable / Disable toggle and time controls shared by every simulator. */
export function robotControls(parent, { scheduler, loop, onDisable }) {
  const box = section(parent, "Driver Station");
  const row = el("div", "sim-row");
  const enable = button(row, {
    label: "Enable",
    kind: "enable",
    onPress: () => {
      const on = !scheduler.enabled;
      scheduler.setEnabled(on);
      if (!on) onDisable?.();
      enable.querySelector(".sim-button__label").textContent = on ? "Disable" : "Enable";
      enable.classList.toggle("is-on", on);
    },
  });
  const pause = button(row, {
    label: "暫停",
    onPress: () => {
      loop.paused = !loop.paused;
      pause.querySelector(".sim-button__label").textContent = loop.paused ? "繼續" : "暫停";
    },
  });
  box.append(row);
  slider(box, {
    label: "模擬速度",
    min: 0.1,
    max: 1,
    step: 0.1,
    value: 1,
    unit: "×",
    onInput: (v) => { loop.speed = v; },
  });
}

/** Lays out the standard simulator frame: 3D view, charts and side panel. */
export function createLayout(root) {
  root.classList.add("sim");
  const view = el("div", "sim-view");
  const charts = el("div", "sim-charts");
  const panel = el("div", "sim-panel");
  const main = el("div", "sim-main");
  main.append(view, charts);
  root.append(main, panel);
  return { view, charts, panel };
}
