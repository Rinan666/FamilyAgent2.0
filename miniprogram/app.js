App({
  globalData: {
    session: null,
    activeFamily: null,
  },

  onLaunch() {
    this.globalData.session = wx.getStorageSync("fa_session") || null;
    this.globalData.activeFamily = wx.getStorageSync("fa_active_family") || null;
  },

  getSession() {
    return this.globalData.session || wx.getStorageSync("fa_session") || null;
  },

  setSession(session) {
    this.globalData.session = session || null;
    if (session) {
      wx.setStorageSync("fa_session", session);
    } else {
      wx.removeStorageSync("fa_session");
    }
  },

  clearSession() {
    this.globalData.session = null;
    this.globalData.activeFamily = null;
    wx.removeStorageSync("fa_session");
    wx.removeStorageSync("fa_active_family");
  },

  getActiveFamily() {
    return this.globalData.activeFamily || wx.getStorageSync("fa_active_family") || null;
  },

  setActiveFamily(family) {
    this.globalData.activeFamily = family || null;
    if (family) {
      wx.setStorageSync("fa_active_family", family);
    } else {
      wx.removeStorageSync("fa_active_family");
    }
  },
});
