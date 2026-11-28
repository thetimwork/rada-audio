# Changelog

Alpha builds are published under Releases as `alpha-N`, where N is the build number.

## alpha-11
- Device card redesigned: the device sits on its own stage at the top of the card, with its type and connection labelled on it; name, Change, engine and EQ chips and the compare button below.
- New motion for the device picture: it leans further and reacts to small tilts of the phone, smoothed so it doesn't shake, overshoots a touch and settles back straight. It floats gently when the phone is still. Tap it and it hops. The quarter-turn on connect lands with a small wobble. All motion off when the phone's animations are off.

## alpha-10
- New device card: the first time a Bluetooth device connects, Rada asks once for its name (filled in from Bluetooth) and type (Speaker, Headphones or Earbuds), then remembers it. The type comes pre-selected from what the device reports when it pairs. Change on the device card edits it later. Closing the card without saving uses the suggestion for now and asks again next time.
- Speakers no longer show as headphones: the type picks the picture and the effects.
- Device pictures: one sharp still per device that makes a single quarter-turn when the device connects, then rests and leans a few degrees as you tilt the phone. Replaces the 360° turntable, which juddered. Still only when the phone's animations are off.
- New speaker model for speakers; the phone picture is now only for the phone's own speaker.
- Crossfeed and virtual surround are headphone effects, so they switch off on speakers, including the phone speaker.
- Smaller app: 1.7 MB of device pictures instead of 3.1 MB.

## alpha-9
- Wording that promised unbuilt features corrected: noise control, battery and codec note, and the compare button (it switches effects off; it does not match loudness).
- Manual and screenshots in `docs/`, new README, changelog, signing-key note; unused logo image removed.

## alpha-8
- 3D devices: softer floor shadow that fades before the edges, so it no longer shows as a box on the warm white card in dark mode.

## alpha-7
- Device card shows a rendered 3D model of the output (headphones, earbuds, USB DAC, phone) that turns slowly and can be dragged.
- Engine and EQ-reach chips on the device card open Advanced setup and Engine details.
- Output names come from the paired Bluetooth device, including your own rename.
- Light/dark switch moved next to the power switch.
- Room card says what reverb is reaching; picking a room turns reverb on.
- Advanced engine (Android 10+, via Shizuku): captures apps that allow it, processes them with Rada's own rooms, virtual surround, crossfeed, true mono, warmth and levelling, and mutes the original. Safety stop if capture goes silent.
- Apps sheet lists what is really being processed. HRTF spatial and convolution marked "Not built yet".
- (alpha-6 did not build; fixed in alpha-7.)

## alpha-5
- Flat redesign in the Rada brand: light and dark themes, Figtree, 16 px cards.
- Equaliser with tall faders, grip caps, dB scale and a smooth curve through the caps; EQ chart on Home.
- Vinyl-radar room view, flat device artwork, Rada wordmark.
- About tab with a QR code to ecpartners.co.
- App name is now Rada Audio.

## alpha-4
- Saved profiles: name, save, load and delete sounds for headphones, earphones, USB DACs and speakers.

## alpha-3
- Redesigned interface with adaptive layouts for phone, foldable and tablet.
- Room reverb and Android's virtualiser; Shizuku setup screens.

## alpha-2
- First Standard-engine build: 10-band EQ, presets, limiter, compressor, settings per output. (alpha-1 did not build.)
