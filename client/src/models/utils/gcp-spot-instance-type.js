import {action, computed, observable} from 'mobx';
import {ContextualPreferenceSearch, names as ContextualPreferences} from './ContextualPreference';

async function getGcpSpotInstanceTypeName () {
  try {
    const request = new ContextualPreferenceSearch();
    await request.send({
      preferences: [ContextualPreferences.gcpSpotInstanceType]
    });
    if (request.loaded && request.value) {
      const {
        type,
        value
      } = request.value;
      let result = String(value);
      if (/^object$/i.test(type)) {
        try {
          result = JSON.parse(value);
        } catch {
          // noop
        }
      }
      return result;
    }
  } catch (error) {
    // empty
  }
  return undefined;
}

export class GcpSpotInstanceType {
  static PREEMPTIBLE = 'PREEMPTIBLE';
  static SPOT = 'SPOT';
  @observable value = GcpSpotInstanceType.PREEMPTIBLE;

  constructor () {
    (this.fetch)();
  }

  @action
  async fetch () {
    this.value = (await getGcpSpotInstanceTypeName()) || GcpSpotInstanceType.SPOT;
  }

  @computed
  get spotName () {
    return this.value === GcpSpotInstanceType.PREEMPTIBLE ? 'Preemptible' : 'Spot';
  }
}

const gcpSpotInstanceType = observable(new GcpSpotInstanceType());

export {gcpSpotInstanceType};
