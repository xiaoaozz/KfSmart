<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';

interface Props {
  /** 粒子数量 */
  particleCount?: number;
  /** 粒子颜色 */
  particleColor?: string;
  /** 粒子大小 */
  particleSize?: number;
  /** 动画速度 */
  speed?: number;
  /** 连线距离 */
  connectDistance?: number;
  /** 是否启用交互 */
  interactive?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  particleCount: 50,
  particleColor: 'rgba(255, 255, 255, 0.5)',
  particleSize: 2,
  speed: 0.5,
  connectDistance: 150,
  interactive: true
});

const canvasRef = ref<HTMLCanvasElement>();
let ctx: CanvasRenderingContext2D | null = null;
let particles: Particle[] = [];
let animationId: number;
let mouseX = 0;
let mouseY = 0;

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
}

function initCanvas() {
  if (!canvasRef.value) return;
  
  ctx = canvasRef.value.getContext('2d');
  if (!ctx) return;

  resizeCanvas();
  createParticles();
  animate();
}

function resizeCanvas() {
  if (!canvasRef.value) return;
  
  canvasRef.value.width = window.innerWidth;
  canvasRef.value.height = window.innerHeight;
}

function createParticles() {
  particles = [];
  
  for (let i = 0; i < props.particleCount; i++) {
    particles.push({
      x: Math.random() * (canvasRef.value?.width || window.innerWidth),
      y: Math.random() * (canvasRef.value?.height || window.innerHeight),
      vx: (Math.random() - 0.5) * props.speed,
      vy: (Math.random() - 0.5) * props.speed,
      radius: props.particleSize
    });
  }
}

function drawParticle(particle: Particle) {
  if (!ctx) return;
  
  ctx.beginPath();
  ctx.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2);
  ctx.fillStyle = props.particleColor;
  ctx.fill();
}

function drawLine(p1: Particle, p2: Particle, distance: number) {
  if (!ctx) return;
  
  const opacity = 1 - distance / props.connectDistance;
  ctx.beginPath();
  ctx.moveTo(p1.x, p1.y);
  ctx.lineTo(p2.x, p2.y);
  ctx.strokeStyle = `rgba(255, 255, 255, ${opacity * 0.2})`;
  ctx.lineWidth = 0.5;
  ctx.stroke();
}

function updateParticle(particle: Particle) {
  if (!canvasRef.value) return;
  
  particle.x += particle.vx;
  particle.y += particle.vy;

  // 边界检测
  if (particle.x < 0 || particle.x > canvasRef.value.width) {
    particle.vx *= -1;
  }
  if (particle.y < 0 || particle.y > canvasRef.value.height) {
    particle.vy *= -1;
  }

  // 交互:粒子靠近鼠标时加速
  if (props.interactive) {
    const dx = mouseX - particle.x;
    const dy = mouseY - particle.y;
    const distance = Math.sqrt(dx * dx + dy * dy);
    
    if (distance < 100) {
      particle.vx += dx * 0.0001;
      particle.vy += dy * 0.0001;
    }
  }

  // 限制速度
  const maxSpeed = props.speed * 2;
  const speed = Math.sqrt(particle.vx * particle.vx + particle.vy * particle.vy);
  if (speed > maxSpeed) {
    particle.vx = (particle.vx / speed) * maxSpeed;
    particle.vy = (particle.vy / speed) * maxSpeed;
  }
}

function animate() {
  if (!ctx || !canvasRef.value) return;
  
  ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height);

  // 更新和绘制粒子
  particles.forEach(particle => {
    updateParticle(particle);
    drawParticle(particle);
  });

  // 绘制连线
  for (let i = 0; i < particles.length; i++) {
    for (let j = i + 1; j < particles.length; j++) {
      const dx = particles[i].x - particles[j].x;
      const dy = particles[i].y - particles[j].y;
      const distance = Math.sqrt(dx * dx + dy * dy);

      if (distance < props.connectDistance) {
        drawLine(particles[i], particles[j], distance);
      }
    }
  }

  animationId = requestAnimationFrame(animate);
}

function handleMouseMove(event: MouseEvent) {
  mouseX = event.clientX;
  mouseY = event.clientY;
}

function handleResize() {
  resizeCanvas();
  createParticles();
}

onMounted(() => {
  initCanvas();
  window.addEventListener('resize', handleResize);
  if (props.interactive) {
    window.addEventListener('mousemove', handleMouseMove);
  }
});

onUnmounted(() => {
  cancelAnimationFrame(animationId);
  window.removeEventListener('resize', handleResize);
  window.removeEventListener('mousemove', handleMouseMove);
});
</script>

<template>
  <canvas
    ref="canvasRef"
    class="particles-canvas absolute inset-0 pointer-events-none"
  />
</template>

<style scoped>
.particles-canvas {
  z-index: 1;
}
</style>
