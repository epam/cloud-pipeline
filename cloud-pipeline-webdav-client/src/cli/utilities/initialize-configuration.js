const initializeConfiguration = require("../../shared/configuration/initialize");

module.exports = async function () {
  return initializeConfiguration(process.cwd());
};
