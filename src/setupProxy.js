const { createProxyMiddleware } = require('http-proxy-middleware');

const appIp = '100.92.215.86';

console.log('[proxy] Using app1 IP:', appIp);

module.exports = function (app) {
  app.use(
    '/api',
    createProxyMiddleware({
      target: `http://${appIp}:8080`,
      changeOrigin: true,
      pathRewrite: { '^/api': '' },
      logLevel: 'debug',
    })
  );
};
