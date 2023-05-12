import React, {useEffect, useState} from 'react';

export function useTimer(enabled) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    if (enabled) {
      setSeconds(0);
      const timer = setInterval(setSeconds, 1000, o => o + 1);
      return () => {
        clearInterval(timer);
      }
    }
    return () => {};
  }, [enabled, setSeconds]);
  return seconds;
}

export default function Timer ({className, enabled, seconds}) {
  if (enabled) {
    return (
      <div className={className}>
        {seconds > 9 ? seconds : `0${seconds}`}
      </div>
    );
  }
  return null;
}
