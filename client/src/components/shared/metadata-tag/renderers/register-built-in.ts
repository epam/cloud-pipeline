import {registerRenderer} from './registry.ts';
import {METADATA_TAG_KEYS} from './tags.ts';
import {LimitMountsRenderer} from './limit-mounts-renderer.tsx';
import {FsNotificationsRenderer} from './fs-notifications-renderer.tsx';
import {MuteEmailRenderer} from './mute-email-renderer.tsx';
import {DavMountRenderer} from './dav-mount-renderer.tsx';
import {RunCapabilitiesRenderer} from './run-capabilities-renderer.tsx';

function registerBuiltInRenderers(): void {
  registerRenderer(METADATA_TAG_KEYS.limitMounts, LimitMountsRenderer);
  registerRenderer(METADATA_TAG_KEYS.runCapabilities, RunCapabilitiesRenderer);
  registerRenderer(METADATA_TAG_KEYS.fsNotifications, FsNotificationsRenderer);
  registerRenderer(METADATA_TAG_KEYS.davMount, DavMountRenderer);
  registerRenderer(METADATA_TAG_KEYS.muteEmailNotifications, MuteEmailRenderer);
}

registerBuiltInRenderers();
