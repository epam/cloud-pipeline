import getUsers from './get-users';

export default function getUser(userName) {
  return new Promise((resolve, reject) => {
    getUsers()
      .then(users => {
        const [user] = users.filter(u => `${u.userName}`.toLowerCase() === `${userName}`.toLowerCase());
        if (user) {
          resolve(user);
        } else {
          reject(new Error(`User ${userName} not found`));
        }
      })
      .catch(reject);
  });
}
