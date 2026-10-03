//! Hardware-independent protocol code used by the Windows daemon.
//! Kept buildable on Linux to validate incoming Bluetooth data before shipping.
pub mod aap;
pub mod hr;

#[cfg(test)]
mod tests {
    use super::{aap, hr};

    // Wire-shaped fixture: a header, component count and five-byte records.
    const BATTERY: &[u8] = &[4, 0, 4, 0, 4, 0, 3,
        4, 1, 80, 2, 1, 2, 1, 60, 1, 1, 8, 1, 255, 4, 1];

    #[test]
    fn battery_distinguishes_missing_case_from_zero() {
        let b = aap::parse_battery(BATTERY).unwrap();
        assert_eq!((b.left, b.right, b.case), (Some(80), Some(60), None));
        assert!(b.right_charging);
        let mut p = BATTERY.to_vec();
        p[19] = 0;
        p[20] = 2;
        assert_eq!(aap::parse_battery(&p).unwrap().case, Some(0));
    }

    #[test]
    fn battery_rejects_truncation_counts_and_bad_levels() {
        for n in 0..BATTERY.len() { assert!(aap::parse_battery(&BATTERY[..n]).is_none()); }
        for (index, value) in [(6, 9), (9, 101), (8, 0), (11, 0)] {
            let mut p = BATTERY.to_vec();
            p[index] = value;
            assert!(aap::parse_battery(&p).is_none());
        }
    }

    fn heart_rate_frame(log_type: u8, service: u8, bpm: u8, confidence: u8) -> Vec<u8> {
        let mut sample = vec![0; 18];
        sample[1] = bpm;
        sample[2] = confidence;
        sample[15] = 0x10;
        let mut command = vec![0x08, service, 0x1a, 18];
        command.extend(sample);
        let mut payload = vec![0x10, log_type, 0x2a, command.len() as u8];
        payload.extend(command);
        let mut frame = vec![4, 0, 4, 0, 0x17, 0, 0, 0, 0x10, 0];
        frame.extend((payload.len() as u16).to_le_bytes());
        frame.extend(payload);
        frame
    }

    #[test]
    fn heart_rate_survives_every_split_boundary() {
        let frame = heart_rate_frame(1, 19, 72, 200);
        for split in 0..=frame.len() {
            let mut d = hr::RtBuddyHeartRateDecoder::new();
            let mut samples = d.feed(&frame[..split]);
            samples.extend(d.feed(&frame[split..]));
            assert_eq!(samples, vec![hr::HrSample { bpm: 72, confidence: 200 }]);
        }
    }

    #[test]
    fn heart_rate_rejects_control_frames_low_confidence_and_invalid_bpm() {
        for (log, service, bpm, confidence) in [(2, 19, 72, 200), (1, 16, 72, 200),
            (1, 19, 72, 20), (1, 19, 0, 200), (1, 19, 255, 200)] {
            let mut d = hr::RtBuddyHeartRateDecoder::new();
            assert!(d.feed(&heart_rate_frame(log, service, bpm, confidence)).is_empty());
        }
    }

    #[test]
    fn decoder_reset_discards_previous_session_fragments() {
        let frame = heart_rate_frame(3, 20, 65, 160);
        let mut d = hr::RtBuddyHeartRateDecoder::new();
        assert!(d.feed(&frame[..20]).is_empty());
        d.reset();
        assert!(d.feed(&frame[20..]).is_empty());
        assert_eq!(d.feed(&frame).len(), 1);
    }
}
