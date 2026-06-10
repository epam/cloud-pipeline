export class NetworkError extends Error {}

export class AuthorizationError extends Error {
  constructor() {
    super('Unauthorized');
  }
}

export class ApiError extends Error {}

export class ApiInitializationError extends Error {}
