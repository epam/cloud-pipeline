interface EventLike {
  stopPropagation(): void;
  preventDefault(): void;
}

export function preventDefaultAndStopPropagation(event: EventLike | undefined): void {
  if (event) {
    event.stopPropagation();
    event.preventDefault();
  }
}

export function stopPropagation(event: EventLike | undefined): void {
  if (event) {
    event.stopPropagation();
  }
}

export function preventDefault(event: EventLike | undefined): void {
  if (event) {
    event.stopPropagation();
  }
}
