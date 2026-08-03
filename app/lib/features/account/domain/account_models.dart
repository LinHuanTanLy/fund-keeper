enum AccountPlatform {
  alipay('ALIPAY'),
  tiantianFund('TIANTIAN_FUND'),
  bank('BANK'),
  other('OTHER');

  const AccountPlatform(this.apiValue);

  final String apiValue;

  static AccountPlatform fromApiValue(String value) {
    return AccountPlatform.values.firstWhere(
      (platform) => platform.apiValue == value,
      orElse: () => AccountPlatform.other,
    );
  }
}

class AccountDraft {
  const AccountDraft({required this.name, required this.platform});

  final String name;
  final AccountPlatform platform;

  Map<String, Object?> toJson() => {
    'name': name,
    'platform': platform.apiValue,
  };
}
