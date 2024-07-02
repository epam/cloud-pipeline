/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

export default function attachAnimation(config) {
  const {
    animate = false,
    intensity = 0.1
  } = config || {};
  if (!animate) {
    return;
  }
  const container = document.getElementById('rain');
  const windowHeight = window.innerHeight;
  const windowWidth = window.innerWidth;
  const count = Math.min(windowWidth / 2.0, Math.round(windowWidth * intensity / 2.0));
  const defaultSpeedPxPerSecond = 700;
  for (let i = 0; i < count; i += 1) {
    const rainDrop = document.createElement('div');
    rainDrop.classList.add('rain-drop');
    rainDrop.style.position = 'absolute';
    const x = Math.round(Math.random() * 100);
    const size = Math.random();
    const height = 10 + Math.round(size * 10);
    const speedPxPerSec = (1.0 + (0.5 - size)) * defaultSpeedPxPerSecond;
    rainDrop.style.left = `${Math.round(x)}%`;
    rainDrop.style.animationDuration = `${Math.round(windowHeight / (speedPxPerSec / 1000))}ms`;
    rainDrop.style.height = `${height}px`;
    const timeout = Math.round(Math.random() * 5000);
    setTimeout(() => container.appendChild(rainDrop), timeout);
  }
}
