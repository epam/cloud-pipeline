const isDevelopmentMode = `${process.env.WEBPACK_DEV_SERVER}` === 'true';
const isProductionMode = !isDevelopmentMode;

module.exports = {
  isDevelopmentMode,
  isProductionMode
};
