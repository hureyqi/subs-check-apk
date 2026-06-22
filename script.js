// ==========================================
// Subs Check APK - UI Mockup Interactions
// ==========================================

document.addEventListener('DOMContentLoaded', () => {
  // Add ripple effect to buttons
  document.querySelectorAll('.btn-primary, .btn-save, .cancel-btn, .icon-btn').forEach(btn => {
    btn.addEventListener('click', function(e) {
      // Create ripple
      const ripple = document.createElement('span');
      const rect = this.getBoundingClientRect();
      const size = Math.max(rect.width, rect.height);
      const x = e.clientX - rect.left - size / 2;
      const y = e.clientY - rect.top - size / 2;
      
      ripple.style.cssText = `
        position: absolute;
        width: ${size}px;
        height: ${size}px;
        left: ${x}px;
        top: ${y}px;
        background: rgba(255,255,255,0.3);
        border-radius: 50%;
        transform: scale(0);
        animation: rippleEffect 0.6s ease-out;
        pointer-events: none;
      `;
      
      this.style.position = 'relative';
      this.style.overflow = 'hidden';
      this.appendChild(ripple);
      
      setTimeout(() => ripple.remove(), 600);
    });
  });

  // Add ripple keyframes
  const style = document.createElement('style');
  style.textContent = `
    @keyframes rippleEffect {
      to {
        transform: scale(2);
        opacity: 0;
      }
    }
  `;
  document.head.appendChild(style);

  // Button hover states
  document.querySelectorAll('.btn-primary').forEach(btn => {
    btn.addEventListener('mouseenter', () => {
      btn.style.filter = 'brightness(1.05)';
    });
    btn.addEventListener('mouseleave', () => {
      btn.style.filter = 'brightness(1)';
    });
  });

  // Animate progress bar on load
  const progressBar = document.querySelector('.progress-bar');
  if (progressBar) {
    // Start from 0
    progressBar.style.width = '0%';
    setTimeout(() => {
      progressBar.style.width = '35%';
    }, 800);
  }

  // Animate stat values on load
  const animateValue = (element, start, end, duration, suffix = '') => {
    const startTime = performance.now();
    const update = (currentTime) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      // Ease out cubic
      const eased = 1 - Math.pow(1 - progress, 3);
      const current = Math.round(start + (end - start) * eased);
      element.textContent = current + suffix;
      if (progress < 1) {
        requestAnimationFrame(update);
      }
    };
    requestAnimationFrame(update);
  };

  // Trigger stat animations after a delay
  setTimeout(() => {
    const statValues = document.querySelectorAll('.stat-value');
    if (statValues.length >= 3) {
      animateValue(statValues[0], 0, 500, 1200);
      animateValue(statValues[1], 0, 175, 1200);
      animateValue(statValues[2], 0, 89, 1200);
    }
  }, 500);

  // Add subtle glow to active progress
  const progressSection = document.querySelector('.progress-section');
  if (progressSection) {
    progressSection.style.position = 'relative';
    const glow = document.createElement('div');
    glow.style.cssText = `
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 3px;
      background: linear-gradient(90deg, transparent, #6750A4, transparent);
      border-radius: 3px 3px 0 0;
      opacity: 0.8;
    `;
    progressSection.appendChild(glow);
  }

  // Scroll log to bottom after animations
  const logContent = document.querySelector('.log-content');
  if (logContent) {
    setTimeout(() => {
      logContent.scrollTop = logContent.scrollHeight;
    }, 1500);
  }
});
