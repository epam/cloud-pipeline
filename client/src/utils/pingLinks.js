const urls = {
  'US-East (Virginia)': 'https://dynamodb.us-east-2.amazonaws.com/ping',
  'US East (Ohio)': 'https://dynamodb.us-east-1.amazonaws.com/ping',
  'US-West (California)': 'https://dynamodb.us-west-1.amazonaws.com/ping',
  'US-West (Oregon)': 'https://dynamodb.us-west-2.amazonaws.com/ping',
  'Canada (Central)': 'https://dynamodb.ca-central-1.amazonaws.com/ping',
  'Europe (Ireland)': 'https://dynamodb.eu-west-1.amazonaws.com/ping',
  'Europe (London)': 'https://dynamodb.eu-west-2.amazonaws.com/ping',
  'Europe (Frankfurt)': 'https://dynamodb.eu-central-1.amazonaws.com/ping',
  'Europe (Paris)': 'https://dynamodb.eu-west-3.amazonaws.com/ping',
  'Europe (Stockholm)': 'https://dynamodb.eu-north-1.amazonaws.com/ping',
  'Middle East (Bahrain)': 'https://dynamodb.me-south-1.amazonaws.com/ping',
  'Asia Pacific (Hong Kong)': 'https://dynamodb.ap-east-1.amazonaws.com/ping',
  'Asia Pacific (Mumbai)': 'https://dynamodb.ap-south-1.amazonaws.com/ping',
  'Asia Pacific (Osaka-Local)': 'https://dynamodb.ap-northeast-3.amazonaws.com/ping',
  'Asia Pacific (Seoul)': 'https://dynamodb.ap-northeast-2.amazonaws.com/ping',
  'Asia Pacific (Singapore)': 'https://dynamodb.ap-southeast-1.amazonaws.com/ping',
  'Asia Pacific (Sydney)': 'https://dynamodb.ap-southeast-2.amazonaws.com/ping',
  'Asia Pacific (Tokyo)': 'https://dynamodb.ap-northeast-1.amazonaws.com/ping',
  'South America (São Paulo)': 'https://dynamodb.sa-east-1.amazonaws.com/ping',
  'China (Beijing)': 'https://dynamodb.cn-north-1.amazonaws.com.cn/ping',
  'China (Ningxia)': 'https://dynamodb.cn-northwest-1.amazonaws.com.cn/ping',
  'AWS GovCloud (US-East)': 'https://dynamodb.us-gov-east-1.amazonaws.com/ping',
  'AWS GovCloud (US)': 'https://dynamodb.us-gov-west-1.amazonaws.com/ping'
};

async function measureUrlLatency (url) {
  return new Promise((resolve, reject) => {
    let xhr = new XMLHttpRequest();
    xhr.onload = () => {
      if (performance !== undefined) {
        const resources = performance.getEntriesByType('resource');
        const latency = resources.filter(
          resource => resource.initiatorType === 'xmlhttprequest' && resource.name === url
        )[0].duration;
        if (latency) {
          resolve({url, latency});
        } else {
          reject(new Error(`Can't get latency for ${url}`));
        }
        performance.clearResourceTimings();
      }
    };
    xhr.onerror = reject;
    xhr.open('GET', url);
    xhr.send();
  });
}

export default async function pingLocation () {
  return Promise.all(
    Object.entries(urls).map(([region, url]) => {
      return measureUrlLatency(url)
        .then(res => ({region, ...res}));
    })
  ).catch(err => console.log(err));
}
