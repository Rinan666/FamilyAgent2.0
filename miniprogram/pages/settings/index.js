const { logout } = require("../../utils/auth");

Page({
  data: {
    session: null,
    activeFamily: null,
  },

  onShow() {
    const app = getApp();
    if (!app.getSession()) {
      wx.reLaunch({ url: "/pages/login/index" });
      return;
    }
    this.setData({
      session: app.getSession(),
      activeFamily: app.getActiveFamily(),
    });
  },

  goFamilies() {
    wx.switchTab({
      url: "/pages/families/index",
    });
  },

  goPrivacyPage() {
    wx.navigateTo({
      url: "/pages/privacy/index",
    });
  },

  openPrivacyContract() {
    if (wx.openPrivacyContract) {
      wx.openPrivacyContract({
        fail: () => {
          wx.showToast({
            title: "隐私指引暂不可用",
            icon: "none",
          });
        },
      });
      return;
    }

    wx.showToast({
      title: "当前基础库不支持打开隐私指引",
      icon: "none",
    });
  },

  handleLogout() {
    logout();
    wx.reLaunch({
      url: "/pages/login/index",
    });
  },
});
