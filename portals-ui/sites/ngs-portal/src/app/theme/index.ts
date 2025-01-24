import colors from './colors';

const text = colors.slate700;
const primary = colors.sky500;
const primaryHover = colors.sky400;
const bg = '#F5F6FA';
const success = colors.green500;
const error = colors.red500;
const info = colors.sky500;
const warning = colors.orange500;

const theme = {
  cssVar: true,
  token: {
    colorText: text,
    colorPrimary: primary,
    colorPrimaryHover: primaryHover,
    colorLink: primary,
    colorLinkHover: primaryHover,
    colorInfoText: primary,
    colorPrimaryText: primary,
    borderRadius: 3,
    colorBgElevated: bg,
    colorBorderBg: bg,
    colorBgContainer: bg,
    colorSuccess: success,
    colorWarning: warning,
    colorError: error,
    colorInfo: info,
  },
};

export default theme;
