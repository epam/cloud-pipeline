import React, {useEffect, useState} from 'react';

export function useTimer(enabled) {
  const [seconds, setSeconds] = useState(0);
  const [start, setStart] = useState(undefined);
  useEffect(() => {
    if (enabled) {
      setSeconds(0);
      setStart(performance.now());
      const timer = setInterval(setSeconds, 1000, o => o + 1);
      return () => {
        clearInterval(timer);
      }
    }
    return () => {};
  }, [enabled, setSeconds, setStart]);
  return {seconds, start};
}

const MINUTES_THRESHOLD = 180;

function formatNumber(number) {
  if (number < 10) {
    return `0${number}`;
  }
  return `${number}`;
}

function getSecondsString(rawSeconds) {
  const seconds = Math.floor(rawSeconds);
  if (seconds < MINUTES_THRESHOLD) {
    return formatNumber(seconds);
  }
  const totalMinutes = Math.floor(seconds / 60.0);
  const hours = Math.floor(totalMinutes / 60.0);
  const minutes = totalMinutes - hours * 60;
  const secs = seconds - totalMinutes * 60;
  if (hours > 0) {
    return `${hours}h ${formatNumber(minutes)}m ${formatNumber(secs)}s`;
  }
  return `${minutes}m ${formatNumber(secs)}s`;
}

export default function Timer ({className, enabled, start}) {
  const [secs, setSecs] = useState(0);
  useEffect(() => {
    if (enabled && start) {
      let t;
      const update = () => {
        setSecs((performance.now() - start) / 1000.0);
        t = setTimeout(update, 10);
      };
      update();
      return () => clearTimeout(t);
    }
    return () => {};
  }, [enabled, setSecs, start]);
  if (enabled) {
    return (
      <div className={className}>
        {getSecondsString(secs)}
      </div>
    );
  }
  return null;
}
