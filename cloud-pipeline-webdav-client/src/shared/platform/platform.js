class Platform {
  constructor(platform = process.platform) {
    this.platform = platform;
  }

  get isWindows() {
    return /^win/i.test(this.platform);
  }

  get isMacOS() {
    return /^darwin$/i.test(this.platform);
  }

  get isLinux() {
    return /^linux$/i.test(this.platform);
  }
}

module.exports = Platform;
