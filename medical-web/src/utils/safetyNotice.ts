import type { SafetyNoticeVO } from '../types/entities'

/**
 * The notice shown before — or instead of — the server's reviewed wording (Round 54.4
 * review). It is fixed in the client so it is always on screen: a chat that cannot
 * reach the notice endpoint must still say what to do in an emergency. Version 0
 * marks it as the local fallback rather than the versioned server copy.
 */
export const FALLBACK_SAFETY_NOTICE: SafetyNoticeVO = {
  version: 0,
  emergencyNotice: 'If this is a medical emergency, call 911 or go to the nearest emergency department now. '
    + 'This messaging channel is not monitored around the clock and is not an emergency service.',
  responseNotice: 'Messages are read by clinic staff during business hours, so a reply may take time. '
    + 'Nothing sent here is medical advice until a clinician has reviewed it.',
}

/** What to render: the server's notice once it arrives, the fixed one until then (and if it never does). */
export function displayedSafetyNotice(fetched: SafetyNoticeVO | null | undefined): SafetyNoticeVO {
  return fetched ?? FALLBACK_SAFETY_NOTICE
}
