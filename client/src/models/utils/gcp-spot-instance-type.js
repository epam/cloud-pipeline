import {computed, observable, makeObservable, action} from 'mobx';
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
  value = GcpSpotInstanceType.PREEMPTIBLE;

  constructor () {
    makeObservable(this, {
      value: observable,
      spotName: computed,
      fetch: action
    });
    (this.fetch)();
  }

  async fetch () {
    this.value = (await getGcpSpotInstanceTypeName()) || GcpSpotInstanceType.SPOT;
  }

  get spotName () {
    return this.value === GcpSpotInstanceType.PREEMPTIBLE ? 'Preemptible' : 'Spot';
  }
}

const gcpSpotInstanceType = observable(new GcpSpotInstanceType());

export {gcpSpotInstanceType};
