import configuration from './config';

function authenticate() {
  window.location = `${configuration.restAPI}/route?url=${window.location.href}&type=COOKIE`;
}

function request(uri, method, body) {
  return new Promise((resolve) => {
    const url = `${configuration.restAPI}/${uri}`;
    const options = {
      mode: 'cors',
      credentials: 'include',
      method: method || (body ? 'POST' : 'GET'),
      headers: {
        'Content-Type': 'application/json; charset=UTF-8;'
      },
      body: body ? JSON.stringify(body) : undefined
    };
    fetch(url, options)
      .then(result => {
        if (result.status === 401) {
          authenticate();
        } else {
          result.json()
            .then(json => {
              const {status, payload, message} = json;
              if (status === 401) {
                authenticate();
              } else if (/^ok$/i.test(status)) {
                resolve(payload);
              } else {
                resolve({error: message || 'Error fetching data'});
              }
            })
            .catch(e => {
              resolve({error: e.toString()});
            });
        }
      })
      .catch(error => {
        resolve({error: error.toString()});
      });
  });
}

export default request;
