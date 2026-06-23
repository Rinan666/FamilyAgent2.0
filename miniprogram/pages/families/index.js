const { request } = require("../../utils/request");

Page({
  data: {
    families: [],
    loading: false,
    error: "",
    createName: "",
    createDescription: "",
    joinCode: "",
    activeFamilyId: null,
  },

  onShow() {
    const app = getApp();
    if (!app.getSession()) {
      wx.reLaunch({ url: "/pages/login/index" });
      return;
    }
    this.setData({
      activeFamilyId: app.getActiveFamily() ? app.getActiveFamily().id : null,
    });
    this.loadFamilies();
  },

  loadFamilies() {
    this.setData({
      loading: true,
      error: "",
    });
    request({
      url: "/families/my",
    }).then((families) => {
      const app = getApp();
      const activeFamily = app.getActiveFamily();
      const nextActive = activeFamily
        ? families.find((item) => item.id === activeFamily.id)
        : null;
      if (nextActive) {
        app.setActiveFamily(nextActive);
      } else if (families.length === 1) {
        app.setActiveFamily(families[0]);
      }
      this.setData({
        families,
        activeFamilyId: app.getActiveFamily() ? app.getActiveFamily().id : null,
      });
    }).catch((error) => {
      this.setData({
        error: error.message || "加载家庭失败",
      });
    }).finally(() => {
      this.setData({
        loading: false,
      });
    });
  },

  handleInput(event) {
    const field = event.currentTarget.dataset.field;
    this.setData({
      [field]: event.detail.value,
    });
  },

  handleSelectFamily(event) {
    const familyId = Number(event.currentTarget.dataset.familyId);
    const family = this.data.families.find((item) => item.id === familyId);
    if (!family) {
      return;
    }
    const app = getApp();
    app.setActiveFamily(family);
    this.setData({
      activeFamilyId: family.id,
    });
    wx.showToast({
      title: "已切换家庭",
      icon: "success",
    });
  },

  handleCreateFamily() {
    if (!this.data.createName.trim()) {
      this.setData({
        error: "请先填写家庭名称",
      });
      return;
    }
    this.setData({
      error: "",
      loading: true,
    });
    request({
      url: "/families",
      method: "POST",
      data: {
        name: this.data.createName.trim(),
        description: this.data.createDescription.trim(),
      },
    }).then((family) => {
      const app = getApp();
      app.setActiveFamily(family);
      this.setData({
        createName: "",
        createDescription: "",
      });
      wx.showToast({
        title: "家庭已创建",
        icon: "success",
      });
      this.loadFamilies();
    }).catch((error) => {
      this.setData({
        error: error.message || "创建家庭失败",
      });
    }).finally(() => {
      this.setData({
        loading: false,
      });
    });
  },

  handleJoinFamily() {
    if (!this.data.joinCode.trim()) {
      this.setData({
        error: "请先填写邀请码",
      });
      return;
    }
    this.setData({
      error: "",
      loading: true,
    });
    request({
      url: `/families/join?inviteCode=${encodeURIComponent(this.data.joinCode.trim())}`,
      method: "POST",
    }).then(() => {
      this.setData({
        joinCode: "",
      });
      wx.showToast({
        title: "加入成功",
        icon: "success",
      });
      this.loadFamilies();
    }).catch((error) => {
      this.setData({
        error: error.message || "加入家庭失败",
      });
    }).finally(() => {
      this.setData({
        loading: false,
      });
    });
  },

  goChat() {
    const app = getApp();
    if (!app.getActiveFamily()) {
      this.setData({
        error: "请先选择一个家庭",
      });
      return;
    }
    wx.switchTab({
      url: "/pages/chat/index",
    });
  },
});
