import Foundation

/// Parses the backend's ISO calendar dates without constructing an ICU date
/// formatter. Zone-less backend values are Istanbul wall time, not device time.
enum BusinessDateParser {
    static let timeZone = TimeZone(identifier: "Europe/Istanbul") ?? TimeZone(secondsFromGMT: 10_800)!
    static let calendar: Calendar = {
        var value = Calendar(identifier: .gregorian)
        value.timeZone = timeZone
        return value
    }()

    static func startOfDay(_ date: Date) -> Date { calendar.startOfDay(for: date) }

    static func parse(_ raw: String?) -> Date? {
        guard let raw, !raw.isEmpty, raw.utf8.count <= 40 else { return nil }
        let bytes = Array(raw.utf8)
        guard bytes.count >= 10, bytes[4] == 45, bytes[7] == 45 else { return nil }
        func number(_ start: Int, _ length: Int) -> Int? {
            guard start + length <= bytes.count else { return nil }
            var result = 0
            for byte in bytes[start..<(start + length)] {
                guard byte >= 48, byte <= 57 else { return nil }
                result = result * 10 + Int(byte - 48)
            }
            return result
        }
        guard let year = number(0, 4), year > 0,
              let month = number(5, 2), (1...12).contains(month),
              let day = number(8, 2), (1...31).contains(day) else { return nil }

        var hour = 0, minute = 0, second = 0
        var fraction = 0.0
        var zone = timeZone
        if bytes.count > 10 {
            guard bytes.count >= 19, bytes[10] == 84,
                  bytes[13] == 58, bytes[16] == 58,
                  let h = number(11, 2), (0...23).contains(h),
                  let m = number(14, 2), (0...59).contains(m),
                  let s = number(17, 2), (0...59).contains(s) else { return nil }
            hour = h; minute = m; second = s
            var cursor = 19
            if cursor < bytes.count, bytes[cursor] == 46 {
                cursor += 1
                let start = cursor
                var scale = 1.0
                while cursor < bytes.count, bytes[cursor] >= 48, bytes[cursor] <= 57 {
                    guard cursor - start < 9 else { return nil }
                    scale *= 10
                    fraction += Double(bytes[cursor] - 48) / scale
                    cursor += 1
                }
                guard cursor > start else { return nil }
            }
            if cursor < bytes.count {
                if bytes[cursor] == 90 {
                    guard cursor + 1 == bytes.count else { return nil }
                    zone = TimeZone(secondsFromGMT: 0)!
                } else {
                    guard bytes[cursor] == 43 || bytes[cursor] == 45 else { return nil }
                    let sign = bytes[cursor] == 43 ? 1 : -1
                    guard let zoneHours = number(cursor + 1, 2), zoneHours <= 18 else { return nil }
                    let hasColon = cursor + 3 < bytes.count && bytes[cursor + 3] == 58
                    let minuteStart = cursor + (hasColon ? 4 : 3)
                    guard minuteStart + 2 == bytes.count,
                          let zoneMinutes = number(minuteStart, 2), zoneMinutes < 60,
                          zoneHours < 18 || zoneMinutes == 0,
                          let offsetZone = TimeZone(secondsFromGMT: sign * (zoneHours * 3600 + zoneMinutes * 60))
                    else { return nil }
                    zone = offsetZone
                }
            }
        }
        var parsingCalendar = calendar
        parsingCalendar.timeZone = zone
        let components = DateComponents(year: year, month: month, day: day,
                                        hour: hour, minute: minute, second: second)
        guard let date = parsingCalendar.date(from: components) else { return nil }
        // Calendar.date normalizes invalid dates; backend values must be exact.
        let checked = parsingCalendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        guard checked.year == year, checked.month == month, checked.day == day,
              checked.hour == hour, checked.minute == minute, checked.second == second else { return nil }
        return date.addingTimeInterval(fraction)
    }
}
