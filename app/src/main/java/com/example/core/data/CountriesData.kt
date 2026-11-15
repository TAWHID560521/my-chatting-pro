package com.example.core.data

data class CountryItem(
    val isoCode: String,
    val name: String,
    val callingCode: String,
    val flagEmoji: String,
    val minDigits: Int = 6,
    val maxDigits: Int = 14
)

object CountriesData {
    val popularIsoCodes = listOf("BD", "US", "GB", "SA", "AE", "IN", "MY", "CA")

    val allCountries: List<CountryItem> = listOf(
        CountryItem("AF", "Afghanistan", "+93", "🇦🇫", 9, 9),
        CountryItem("AL", "Albania", "+355", "🇦🇱", 8, 9),
        CountryItem("DZ", "Algeria", "+213", "🇩🇿", 9, 9),
        CountryItem("AD", "Andorra", "+376", "🇦🇩", 6, 9),
        CountryItem("AO", "Angola", "+244", "🇦🇴", 9, 9),
        CountryItem("AG", "Antigua and Barbuda", "+1268", "🇦🇬", 7, 7),
        CountryItem("AR", "Argentina", "+54", "🇦🇷", 10, 11),
        CountryItem("AM", "Armenia", "+374", "🇦🇲", 8, 8),
        CountryItem("AU", "Australia", "+61", "🇦🇺", 9, 9),
        CountryItem("AT", "Austria", "+43", "🇦🇹", 10, 13),
        CountryItem("AZ", "Azerbaijan", "+994", "🇦🇿", 9, 9),
        CountryItem("BS", "Bahamas", "+1242", "🇧🇸", 7, 7),
        CountryItem("BH", "Bahrain", "+973", "🇧🇭", 8, 8),
        CountryItem("BD", "Bangladesh", "+880", "🇧🇩", 10, 10),
        CountryItem("BB", "Barbados", "+1246", "🇧🇧", 7, 7),
        CountryItem("BY", "Belarus", "+375", "🇧🇾", 9, 9),
        CountryItem("BE", "Belgium", "+32", "🇧🇪", 8, 9),
        CountryItem("BZ", "Belize", "+501", "🇧🇿", 7, 7),
        CountryItem("BJ", "Benin", "+229", "🇧🇯", 8, 8),
        CountryItem("BT", "Bhutan", "+975", "🇧🇹", 8, 8),
        CountryItem("BO", "Bolivia", "+591", "🇧🇴", 8, 8),
        CountryItem("BA", "Bosnia and Herzegovina", "+387", "🇧🇦", 8, 9),
        CountryItem("BW", "Botswana", "+267", "🇧🇼", 7, 8),
        CountryItem("BR", "Brazil", "+55", "🇧🇷", 10, 11),
        CountryItem("BN", "Brunei", "+673", "🇧🇳", 7, 7),
        CountryItem("BG", "Bulgaria", "+359", "🇧🇬", 8, 9),
        CountryItem("BF", "Burkina Faso", "+226", "🇧🇫", 8, 8),
        CountryItem("BI", "Burundi", "+257", "🇧🇮", 8, 8),
        CountryItem("KH", "Cambodia", "+855", "🇰🇭", 8, 9),
        CountryItem("CM", "Cameroon", "+237", "🇨🇲", 9, 9),
        CountryItem("CA", "Canada", "+1", "🇨🇦", 10, 10),
        CountryItem("CV", "Cape Verde", "+238", "🇨🇻", 7, 7),
        CountryItem("CF", "Central African Republic", "+236", "🇨🇫", 8, 8),
        CountryItem("TD", "Chad", "+235", "🇹🇩", 8, 8),
        CountryItem("CL", "Chile", "+56", "🇨🇱", 9, 9),
        CountryItem("CN", "China", "+86", "🇨🇳", 11, 11),
        CountryItem("CO", "Colombia", "+57", "🇨🇴", 10, 10),
        CountryItem("KM", "Comoros", "+269", "🇰🇲", 7, 7),
        CountryItem("CG", "Congo", "+242", "🇨🇬", 9, 9),
        CountryItem("CD", "Congo (DRC)", "+243", "🇨🇩", 9, 9),
        CountryItem("CR", "Costa Rica", "+506", "🇨🇷", 8, 8),
        CountryItem("HR", "Croatia", "+385", "🇭🇷", 8, 9),
        CountryItem("CU", "Cuba", "+53", "🇨🇺", 8, 8),
        CountryItem("CY", "Cyprus", "+357", "🇨🇾", 8, 8),
        CountryItem("CZ", "Czechia", "+420", "🇨🇿", 9, 9),
        CountryItem("DK", "Denmark", "+45", "🇩🇰", 8, 8),
        CountryItem("DJ", "Djibouti", "+253", "🇩🇯", 8, 8),
        CountryItem("DM", "Dominica", "+1767", "🇩🇲", 7, 7),
        CountryItem("DO", "Dominican Republic", "+1809", "🇩🇴", 7, 7),
        CountryItem("EC", "Ecuador", "+593", "🇪🇨", 9, 9),
        CountryItem("EG", "Egypt", "+20", "🇪🇬", 9, 10),
        CountryItem("SV", "El Salvador", "+503", "🇸🇻", 8, 8),
        CountryItem("GQ", "Equatorial Guinea", "+240", "🇬🇶", 9, 9),
        CountryItem("ER", "Eritrea", "+291", "🇪🇷", 7, 7),
        CountryItem("EE", "Estonia", "+372", "🇪🇪", 7, 8),
        CountryItem("SZ", "Eswatini", "+268", "🇸🇿", 8, 8),
        CountryItem("ET", "Ethiopia", "+251", "🇪🇹", 9, 9),
        CountryItem("FJ", "Fiji", "+679", "🇫🇯", 7, 7),
        CountryItem("FI", "Finland", "+358", "🇫🇮", 7, 11),
        CountryItem("FR", "France", "+33", "🇫🇷", 9, 9),
        CountryItem("GA", "Gabon", "+241", "🇬🇦", 7, 8),
        CountryItem("GM", "Gambia", "+220", "🇬🇲", 7, 7),
        CountryItem("GE", "Georgia", "+995", "🇬🇪", 9, 9),
        CountryItem("DE", "Germany", "+49", "🇩🇪", 7, 13),
        CountryItem("GH", "Ghana", "+233", "🇬🇭", 9, 9),
        CountryItem("GR", "Greece", "+30", "🇬🇷", 10, 10),
        CountryItem("GD", "Grenada", "+1473", "🇬🇩", 7, 7),
        CountryItem("GT", "Guatemala", "+502", "🇬🇹", 8, 8),
        CountryItem("GN", "Guinea", "+224", "🇬🇳", 9, 9),
        CountryItem("GW", "Guinea-Bissau", "+245", "🇬🇼", 7, 9),
        CountryItem("GY", "Guyana", "+592", "🇬🇾", 7, 7),
        CountryItem("HT", "Haiti", "+509", "🇭🇹", 8, 8),
        CountryItem("HN", "Honduras", "+504", "🇭🇳", 8, 8),
        CountryItem("HK", "Hong Kong", "+852", "🇭🇰", 8, 8),
        CountryItem("HU", "Hungary", "+36", "🇭🇺", 8, 9),
        CountryItem("IS", "Iceland", "+354", "🇮🇸", 7, 7),
        CountryItem("IN", "India", "+91", "🇮🇳", 10, 10),
        CountryItem("ID", "Indonesia", "+62", "🇮🇩", 9, 12),
        CountryItem("IR", "Iran", "+98", "🇮🇷", 10, 10),
        CountryItem("IQ", "Iraq", "+964", "🇮🇶", 10, 10),
        CountryItem("IE", "Ireland", "+353", "🇮🇪", 7, 9),
        CountryItem("IL", "Israel", "+972", "🇮🇱", 8, 9),
        CountryItem("IT", "Italy", "+39", "🇮🇹", 8, 11),
        CountryItem("CI", "Ivory Coast", "+225", "🇨🇮", 8, 10),
        CountryItem("JM", "Jamaica", "+1876", "🇯🇲", 7, 7),
        CountryItem("JP", "Japan", "+81", "🇯🇵", 9, 10),
        CountryItem("JO", "Jordan", "+962", "🇯🇴", 9, 9),
        CountryItem("KZ", "Kazakhstan", "+7", "🇰🇿", 10, 10),
        CountryItem("KE", "Kenya", "+254", "🇰🇪", 9, 9),
        CountryItem("KI", "Kiribati", "+686", "🇰🇮", 5, 8),
        CountryItem("KW", "Kuwait", "+965", "🇰🇼", 8, 8),
        CountryItem("KG", "Kyrgyzstan", "+996", "🇰🇬", 9, 9),
        CountryItem("LA", "Laos", "+856", "🇱🇦", 8, 10),
        CountryItem("LV", "Latvia", "+371", "🇱🇻", 8, 8),
        CountryItem("LB", "Lebanon", "+961", "🇱🇧", 7, 8),
        CountryItem("LS", "Lesotho", "+266", "🇱🇸", 8, 8),
        CountryItem("LR", "Liberia", "+231", "🇱🇷", 7, 9),
        CountryItem("LY", "Libya", "+218", "🇱🇾", 9, 9),
        CountryItem("LI", "Liechtenstein", "+423", "🇱🇮", 7, 9),
        CountryItem("LT", "Lithuania", "+370", "🇱🇹", 8, 8),
        CountryItem("LU", "Luxembourg", "+352", "🇱🇺", 6, 11),
        CountryItem("MO", "Macau", "+853", "🇲🇴", 8, 8),
        CountryItem("MG", "Madagascar", "+261", "🇲🇬", 9, 9),
        CountryItem("MW", "Malawi", "+265", "🇲🇼", 7, 9),
        CountryItem("MY", "Malaysia", "+60", "🇲🇾", 9, 10),
        CountryItem("MV", "Maldives", "+960", "🇲🇻", 7, 7),
        CountryItem("ML", "Mali", "+223", "🇲🇱", 8, 8),
        CountryItem("MT", "Malta", "+356", "🇲🇹", 8, 8),
        CountryItem("MR", "Mauritania", "+222", "🇲🇷", 8, 8),
        CountryItem("MU", "Mauritius", "+230", "🇲🇺", 8, 8),
        CountryItem("MX", "Mexico", "+52", "🇲🇽", 10, 10),
        CountryItem("MD", "Moldova", "+373", "🇲🇩", 8, 8),
        CountryItem("MC", "Monaco", "+377", "🇲🇨", 8, 9),
        CountryItem("MN", "Mongolia", "+976", "🇲🇳", 8, 8),
        CountryItem("ME", "Montenegro", "+382", "🇲🇪", 8, 8),
        CountryItem("MA", "Morocco", "+212", "🇲🇦", 9, 9),
        CountryItem("MZ", "Mozambique", "+258", "🇲🇿", 9, 9),
        CountryItem("MM", "Myanmar", "+95", "🇲🇲", 7, 10),
        CountryItem("NA", "Namibia", "+264", "🇳🇦", 8, 10),
        CountryItem("NP", "Nepal", "+977", "🇳🇵", 10, 10),
        CountryItem("NL", "Netherlands", "+31", "🇳🇱", 9, 9),
        CountryItem("NZ", "New Zealand", "+64", "🇳🇿", 8, 10),
        CountryItem("NI", "Nicaragua", "+505", "🇳🇮", 8, 8),
        CountryItem("NE", "Niger", "+227", "🇳🇪", 8, 8),
        CountryItem("NG", "Nigeria", "+234", "🇳🇬", 10, 10),
        CountryItem("MK", "North Macedonia", "+389", "🇲🇰", 8, 8),
        CountryItem("NO", "Norway", "+47", "🇳🇴", 8, 8),
        CountryItem("OM", "Oman", "+968", "🇴🇲", 8, 8),
        CountryItem("PK", "Pakistan", "+92", "🇵🇰", 10, 10),
        CountryItem("PS", "Palestine", "+970", "🇵🇸", 9, 9),
        CountryItem("PA", "Panama", "+507", "🇵🇦", 7, 8),
        CountryItem("PG", "Papua New Guinea", "+675", "🇵🇬", 7, 8),
        CountryItem("PY", "Paraguay", "+595", "🇵🇾", 9, 9),
        CountryItem("PE", "Peru", "+51", "🇵🇪", 9, 9),
        CountryItem("PH", "Philippines", "+63", "🇵🇭", 10, 10),
        CountryItem("PL", "Poland", "+48", "🇵🇱", 9, 9),
        CountryItem("PT", "Portugal", "+351", "🇵🇹", 9, 9),
        CountryItem("QA", "Qatar", "+974", "🇶🇦", 8, 8),
        CountryItem("RO", "Romania", "+40", "🇷🇴", 9, 9),
        CountryItem("RU", "Russia", "+7", "🇷🇺", 10, 10),
        CountryItem("RW", "Rwanda", "+250", "🇷🇼", 9, 9),
        CountryItem("SA", "Saudi Arabia", "+966", "🇸🇦", 9, 9),
        CountryItem("SN", "Senegal", "+221", "🇸🇳", 9, 9),
        CountryItem("RS", "Serbia", "+381", "🇷🇸", 8, 9),
        CountryItem("SL", "Sierra Leone", "+232", "🇸🇱", 8, 8),
        CountryItem("SG", "Singapore", "+65", "🇸🇬", 8, 8),
        CountryItem("SK", "Slovakia", "+421", "🇸🇰", 9, 9),
        CountryItem("SI", "Slovenia", "+386", "🇸🇮", 8, 8),
        CountryItem("SO", "Somalia", "+252", "🇸🇴", 7, 9),
        CountryItem("ZA", "South Africa", "+27", "🇿🇦", 9, 9),
        CountryItem("KR", "South Korea", "+82", "🇰🇷", 9, 11),
        CountryItem("SS", "South Sudan", "+211", "🇸🇸", 9, 9),
        CountryItem("ES", "Spain", "+34", "🇪🇸", 9, 9),
        CountryItem("LK", "Sri Lanka", "+94", "🇱🇰", 9, 9),
        CountryItem("SD", "Sudan", "+249", "🇸🇩", 9, 9),
        CountryItem("SR", "Suriname", "+597", "🇸🇷", 6, 7),
        CountryItem("SE", "Sweden", "+46", "🇸🇪", 7, 10),
        CountryItem("CH", "Switzerland", "+41", "🇨🇭", 9, 9),
        CountryItem("SY", "Syria", "+963", "🇸🇾", 9, 9),
        CountryItem("TW", "Taiwan", "+886", "🇹🇼", 8, 9),
        CountryItem("TJ", "Tajikistan", "+992", "🇹🇯", 9, 9),
        CountryItem("TZ", "Tanzania", "+255", "🇹🇿", 9, 9),
        CountryItem("TH", "Thailand", "+66", "🇹🇭", 9, 9),
        CountryItem("TL", "Timor-Leste", "+670", "🇹🇱", 7, 8),
        CountryItem("TG", "Togo", "+228", "🇹🇬", 8, 8),
        CountryItem("TO", "Tonga", "+676", "🇹🇴", 5, 7),
        CountryItem("TT", "Trinidad and Tobago", "+1868", "🇹🇹", 7, 7),
        CountryItem("TN", "Tunisia", "+216", "🇹🇳", 8, 8),
        CountryItem("TR", "Turkey", "+90", "🇹🇷", 10, 10),
        CountryItem("TM", "Turkmenistan", "+993", "🇹🇲", 8, 8),
        CountryItem("UG", "Uganda", "+256", "🇺🇬", 9, 9),
        CountryItem("UA", "Ukraine", "+380", "🇺🇦", 9, 9),
        CountryItem("AE", "United Arab Emirates", "+971", "🇦🇪", 9, 9),
        CountryItem("GB", "United Kingdom", "+44", "🇬🇧", 9, 10),
        CountryItem("US", "United States", "+1", "🇺🇸", 10, 10),
        CountryItem("UY", "Uruguay", "+598", "🇺🇾", 8, 8),
        CountryItem("UZ", "Uzbekistan", "+998", "🇺🇿", 9, 9),
        CountryItem("VE", "Venezuela", "+58", "🇻🇪", 10, 10),
        CountryItem("VN", "Vietnam", "+84", "🇻🇳", 9, 10),
        CountryItem("YE", "Yemen", "+967", "🇾🇪", 9, 9),
        CountryItem("ZM", "Zambia", "+260", "🇿🇲", 9, 9),
        CountryItem("ZW", "Zimbabwe", "+263", "🇿🇼", 9, 9)
    )

    fun byIso(iso: String): CountryItem =
        allCountries.firstOrNull { it.isoCode.equals(iso, ignoreCase = true) }
            ?: allCountries.first { it.isoCode == "BD" }

    /**
     * Normalizes a national phone input + selected country calling code into E.164 format.
     * Strips spaces, dashes, parentheses, and trunk leading '0' when appropriate.
     * Returns null if invalid length or non-digit characters remain.
     */
    fun normalizeToE164(country: CountryItem, rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val codeDigits = country.callingCode.filter { it.isDigit() }
        var digits = trimmed.filter { it.isDigit() }

        if (trimmed.startsWith("+") && digits.startsWith(codeDigits)) {
            digits = digits.removePrefix(codeDigits)
        } else if (digits.startsWith("00$codeDigits")) {
            digits = digits.removePrefix("00$codeDigits")
        }

        // Strip single trunk leading zero common in BD (+880 017...), UK (+44 07...), etc.
        if (digits.startsWith("0") && digits.length > country.minDigits) {
            digits = digits.dropWhile { it == '0' }
        }

        if (digits.length < country.minDigits || digits.length > country.maxDigits) {
            return null
        }

        val e164 = "+$codeDigits$digits"
        // E.164 max 15 digits after '+'
        val totalDigits = e164.count { it.isDigit() }
        return if (totalDigits in 7..15) e164 else null
    }
}
