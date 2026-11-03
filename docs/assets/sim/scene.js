// Three.js scene setup shared by the mechanism simulators.
import * as THREE from "../vendor/three/three.module.min.js";
import { OrbitControls } from "../vendor/three/OrbitControls.js";

export { THREE };

export const COLORS = {
  frame: 0xa9aeb5,
  dark: 0x3c4047,
  moving: 0xf08c2e,
  target: 0x3d8bfd,
  carpet: 0x5c6166,
  red: 0xd9434b,
  blue: 0x3b6fd9,
};

/**
 * Creates a renderer, camera, lights, ground and orbit controls inside
 * `container`. Returns null (and shows a message) when WebGL is unavailable.
 */
export function createScene(container, { cameraPosition = [2, 1.4, 2.4], target = [0, 0.6, 0], groundSize = 6 } = {}) {
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
  } catch {
    container.textContent = "你的瀏覽器不支援 WebGL，無法顯示 3D 畫面。下方的曲線圖和控制面板仍然可以使用。";
    container.classList.add("sim-view--fallback");
    return null;
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.shadowMap.enabled = true;
  container.append(renderer.domElement);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(45, 1, 0.05, 100);
  camera.position.set(...cameraPosition);

  scene.add(new THREE.HemisphereLight(0xffffff, 0x445566, 1.6));
  const sun = new THREE.DirectionalLight(0xffffff, 2.2);
  sun.position.set(3, 6, 4);
  sun.castShadow = true;
  sun.shadow.mapSize.set(1024, 1024);
  Object.assign(sun.shadow.camera, { left: -4, right: 4, top: 4, bottom: -4 });
  scene.add(sun);

  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(groundSize, groundSize),
    new THREE.MeshStandardMaterial({ color: COLORS.carpet, roughness: 1 }),
  );
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  scene.add(ground);
  const grid = new THREE.GridHelper(groundSize, groundSize * 2, 0x888c92, 0x72777d);
  grid.position.y = 0.001;
  scene.add(grid);

  const controls = new OrbitControls(camera, renderer.domElement);
  controls.target.set(...target);
  controls.enableDamping = true;
  controls.maxPolarAngle = Math.PI / 2 - 0.05;

  const resize = () => {
    const w = container.clientWidth;
    const h = container.clientHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  };
  new ResizeObserver(resize).observe(container);
  resize();

  return {
    scene,
    camera,
    renderer,
    render() {
      controls.update();
      renderer.render(scene, camera);
    },
  };
}

export function box(size, color, { opacity = 1 } = {}) {
  const mesh = new THREE.Mesh(
    new THREE.BoxGeometry(...size),
    new THREE.MeshStandardMaterial({
      color,
      roughness: 0.55,
      metalness: 0.2,
      transparent: opacity < 1,
      opacity,
      depthWrite: opacity >= 1,
    }),
  );
  mesh.castShadow = opacity >= 1;
  mesh.receiveShadow = opacity >= 1;
  return mesh;
}

export function cylinder(radius, length, color, { opacity = 1, segments = 32 } = {}) {
  const mesh = new THREE.Mesh(
    new THREE.CylinderGeometry(radius, radius, length, segments),
    new THREE.MeshStandardMaterial({
      color,
      roughness: 0.5,
      metalness: 0.3,
      transparent: opacity < 1,
      opacity,
    }),
  );
  mesh.castShadow = opacity >= 1;
  return mesh;
}
