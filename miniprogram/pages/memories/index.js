const { request } = require("../../utils/request");
const { formatDateTime } = require("../../utils/format");

Page({
  data: {
    activeFamily: null,
    memories: [],
    newContent: "",
    loading: false,
    saving: false,
    error: "",
  },

  onShow() {
    const app = getApp();
    if (!app.getSession()) {
      wx.reLaunch({ url: "/pages/login/index" });
      return;
    }
    const activeFamily = app.getActiveFamily();
    if (!activeFamily) {
      wx.switchTab({ url: "/pages/families/index" });
      return;
    }
    this.setData({
      activeFamily,
    });
    this.loadMemories();
  },

  handleInput(event) {
    this.setData({
      newContent: event.detail.value,
    });
  },

  loadMemories() {
    this.setData({
      loading: true,
      error: "",
    });
    request({
      url: `/memories/family/${this.data.activeFamily.id}?limit=30`,
    }).then((memories) => {
      this.setData({
        memories: memories.map((item) => ({
          ...item,
          displayTime: formatDateTime(item.createdAt),
        })),
      });
    }).catch((error) => {
      this.setData({
        error: error.message || "加载记忆失败",
      });
    }).finally(() => {
      this.setData({
        loading: false,
      });
    });
  },

  handleCreateMemory() {
    const content = this.data.newContent.trim();
    if (!content) {
      this.setData({
        error: "请先输入要保存的内容",
      });
      return;
    }

    this.setData({
      saving: true,
      error: "",
    });
    request({
      url: "/memories/family",
      method: "POST",
      data: {
        familyId: this.data.activeFamily.id,
        content,
      },
    }).then(() => {
      this.setData({
        newContent: "",
      });
      wx.showToast({
        title: "已保存",
        icon: "success",
      });
      this.loadMemories();
    }).catch((error) => {
      this.setData({
        error: error.message || "保存失败",
      });
    }).finally(() => {
      this.setData({
        saving: false,
      });
    });
  },
});
