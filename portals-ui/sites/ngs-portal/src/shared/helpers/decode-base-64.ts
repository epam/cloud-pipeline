export const decodeBase64 = (base64String: string) => {
  const sanitized = base64String
    .replace(/[^A-Za-z0-9+/=]/g, '') // Remove invalid characters
    .replace(/-/g, '+') // Replace URL-safe Base64
    .replace(/_/g, '/');
  return atob(sanitized);
};
