/**
  Copyright (c) 2015, 2026, Oracle and/or its affiliates.
  Licensed under The Universal Permissive License (UPL), Version 1.0
  as shown at https://oss.oracle.com/licenses/upl/

*/

'use strict';

module.exports = function (configObj) {
  return new Promise((resolve, reject) => {
    console.log('Running before_serve hook.');
    // Serve the app shell for known browser routes, never for API or asset URLs.
    const routes = new Set(['/login', '/register', '/admin', '/profile', '/dashboard', '/send-money', '/beneficiaries', '/transactions']);
    configObj.preMiddleware = [...(configObj.preMiddleware || []), (req, res, next) => {
      const pathname = req.url.split('?')[0].replace(/\/$/, '');
      if ((req.method === 'GET' || req.method === 'HEAD') && routes.has(pathname)) {
        req.url = '/index.html';
      }
      next();
    }];
    // ojet custom connect and serve options
    // { connectOpts, serveOpts } = configObj;
    // const express = require('express');
    // const http = require('http');
    // pass back custom http
    // configObj['http'] = http;
    // pass back custom express app
    // configObj['express'] = express();
    // pass back custom options for http.createServer
    // const serverOptions = {...};
    // configObj['serverOptions'] = serverOptions;
    // pass back custom server
    // configObj['server'] = http.createServer(serverOptions, express());
    // const tinylr = require('tiny-lr');
    // pass back custom live reload server
    // configObj['liveReloadServer'] = tinylr({ port: PORT });
    // pass back a replacement set of middleware
    // configObj['middleware'] = [...];
    // pass back a set of middleware that goes before the default middleware
    // configObj['preMiddleware'] = [...];
    // pass back a set of middleware that goes after the default middleware
    // configObj['postMiddleware'] = [...];
    resolve(configObj);
  });
};
