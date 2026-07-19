const { createProxyMiddleware } = require('http-proxy-middleware');

const ratingsIp = '100.92.215.86';

console.log('[proxy] Using ratings IP:', ratingsIp);

module.exports = function (app) {
  app.use(
    '/api',
    createProxyMiddleware({
      target: `http://${ratingsIp}:8080`,
      changeOrigin: true,
      pathRewrite: { '^/api': '' },
      logLevel: 'debug',
    })
  );
};
