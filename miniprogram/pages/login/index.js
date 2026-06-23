const { loginWithWeChat } = require("../../utils/auth");

Page({
  data: {
    nickname: "",
    loading: false,
    error: "",
  },

  onShow() {
    const app = getApp();
    const session = app.getSession();
    if (session) {
      wx.reLaunch({
        url: "/pages/families/index",
      });
    }
  },

  handleNicknameInput(event) {
    this.setData({
      nickname: event.detail.value,
    });
  },

  handleLogin() {
    if (this.data.loading) {
      return;
    }
    this.setData({
      loading: true,
      error: "",
    });

    loginWithWeChat({
      nickname: this.data.nickname,
    }).then(() => {
      wx.reLaunch({
        url: "/pages/families/index",
      });
    }).catch((error) => {
      this.setData({
        error: error.message || "登录失败，请重试",
      });
    }).finally(() => {
      this.setData({
        loading: false,
      });
    });
  },
});
