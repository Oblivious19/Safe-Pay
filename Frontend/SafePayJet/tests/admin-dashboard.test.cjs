const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript'),ko=require('knockout');
class ApiError extends Error {constructor(status,message){super(message);this.status=status;}}
const summary={totalTransactions:2,settledTransactions:1,protectedTransactions:0,hardHolds:1,cancelledTransactions:0,rejectedTransactions:0,highRiskTransactions:1,totalAmount:450000,settledAmount:5000};
const categoryModule={exports:{}};
vm.runInNewContext('(function(module,exports){'+ts.transpileModule(fs.readFileSync(path.join(__dirname,'../src/ts/constants/paymentCategories.ts'),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText+'\n})')(categoryModule,categoryModule.exports);
function fixture(service={},page='dashboard') {
  const calls=[],redirects=[],module={exports:{}};
  const imports={'knockout':ko,'../services/apiError':{ApiError},'../constants/paymentCategories':categoryModule.exports,
    '../services/authService':{authService:{logout:()=>Promise.resolve({message:'ok'})}},
    '../utils/protection':{statusLabel:s=>s,tierRisk:s=>s},
    '../services/adminHoldService':{adminHoldService:{list:()=>Promise.resolve(service.holds ? service.holds() : [])}},
    '../services/adminReportService':{adminReportService:{
    summary:()=>{calls.push(['summary']);return service.summary ? service.summary() : Promise.resolve(summary);},
    daily:(from,to)=>{calls.push(['daily',from,to]);return service.daily ? service.daily() : Promise.resolve([]);},
    book:()=>{calls.push(['book']);return service.book ? service.book() : Promise.resolve({payments:[],users:[]});}
  }}};
  const source=fs.readFileSync(path.join(__dirname,'../src/ts/viewModels/admin.ts'),'utf8');
  const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText;
  vm.runInNewContext('(function(require,module,exports){'+code+'\n})',{Intl,Date,document:{title:''},window:{location:{replace:url=>redirects.push(url)}}})(key=>imports[key],module,module.exports);
  const model=new module.exports({params:{page}});model.from('2026-09-01');model.to('2026-09-15');return {model,calls,redirects};
}
test('authorized reports load exact date range and full INR values',async()=>{
  const f=fixture({daily:async()=>[{date:'2026-09-01',summary},{date:'2026-09-15',summary}]});await f.model.load();
  assert.deepEqual(f.calls,[['summary'],['daily','2026-09-01','2026-09-15'],['book']]);assert.equal(f.model.summary(),summary);
  assert.equal(f.model.daily()[0].date,'2026-09-15');assert.equal(f.model.money(450000),'₹4,50,000.00');assert.equal(f.model.loading(),false);
});
test('customer forbidden state hides all data and skips daily call',async()=>{
  const f=fixture({summary:async()=>{throw new ApiError(403,'Forbidden')}});await f.model.load();
  assert.equal(f.model.forbidden(),true);assert.equal(f.model.summary(),null);assert.equal(f.model.daily().length,0);assert.equal(f.calls.length,1);
});
test('anonymous redirects to admin login',async()=>{const f=fixture({summary:async()=>{throw new ApiError(401,'Login required')}});await f.model.load();assert.deepEqual(f.redirects,['/admin/login']);assert.equal(f.model.summary(),null);});
test('admin login alias reuses existing login with no report requests',()=>{const f=fixture({},'login');f.model.connected();assert.deepEqual(f.redirects,['/login?admin=1&reason=session-expired']);assert.equal(f.calls.length,0);});
test('expiry during daily load clears summary and redirects',async()=>{const f=fixture({daily:async()=>{throw new ApiError(401,'Expired')}});await f.model.load();assert.equal(f.model.summary(),null);assert.deepEqual(f.redirects,['/admin/login']);});
test('empty data remains zero rather than inventing records',async()=>{const f=fixture({summary:async()=>({...summary,totalTransactions:0})});await f.model.load();assert.equal(f.model.summary().totalTransactions,0);assert.equal(f.model.daily().length,0);assert.equal(f.model.error(),'');});
test('network/server error is generic, no fabricated zero totals',async()=>{const f=fixture({summary:async()=>{throw new Error('Internal SQL')}});await f.model.load();assert.match(f.model.error(),/unavailable/);assert.doesNotMatch(f.model.error(),/SQL/);assert.equal(f.model.summary(),null);});
test('invalid dates do not request reports',async()=>{const f=fixture();f.model.from('2026-02-31');await f.model.load();assert.equal(f.calls.length,0);assert.match(f.model.error(),/date range/);});
test('loading blocks duplicate calls, leaving ignores response',async()=>{
  let finish;const f=fixture({summary:()=>new Promise(r=>finish=r)});const pending=f.model.load();assert.equal(f.model.loading(),true);await f.model.load();assert.equal(f.calls.length,1);f.model.disconnected();finish(summary);await pending;assert.equal(f.model.summary(),null);assert.equal(f.calls.length,1);
});
test('clean admin routes have a frontend fallback',async()=>{
  const config=await require('../scripts/hooks/before_serve')({});
  for(const url of ['/admin/dashboard','/admin/login']){const req={method:'GET',url};config.preMiddleware[0](req,{},()=>{});assert.equal(req.url,'/index.html');}
});
test('dashboard charts render outcome bars, daily columns and a hold snapshot',async()=>{
  const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/admin.html'),'utf8');
  const css=fs.readFileSync(path.join(__dirname,'../src/css/admin-dashboard.css'),'utf8');
  assert.match(html,/admin-charts/);assert.match(html,/outcomeBars/);assert.match(html,/dailyBars/);assert.match(html,/pendingHolds/);
  assert.doesNotMatch(html,/admin-ledger-title/);assert.match(html,/highRiskRate/);assert.match(html,/foreach:\s*people/);
  assert.match(css,/admin-bar-track/);assert.match(css,/admin-columns/);
  const held={transactionId:9,transactionRef:'DEMO-9',customerName:'Anika Sharma',beneficiaryName:'Rohan Gupta',amount:105000,createdAt:'2026-09-16T10:00:00.000Z',verification:'OTP_SENT',decision:'PENDING'};
  const row={customerName:'Anika Sharma',customerEmail:'anika.demo@safepay.test',transactionId:9,transactionRef:'DEMO-9',amount:105000,purpose:'Property token',beneficiaryName:'Rohan Gupta',beneficiaryBankAccountNumber:'501234',state:'HARD_HOLD',riskTier:'VERY_HIGH',riskReason:'Amount above INR 1,00,000 (+6); Total score 6: VERY_HIGH',createdAt:'2026-09-16T10:00:00.000Z',settledAt:'',cancelledAt:'',protectionExpiresAt:''};
  const f=fixture({
    daily:async()=>[{date:'2026-09-01',summary},{date:'2026-09-15',summary}],
    holds:async()=>[held],
    book:async()=>({payments:[row],users:[{userId:1,name:'Anika Sharma',email:'anika.demo@safepay.test',role:'CUSTOMER',status:'ACTIVE',accountNumber:'59810010001',balance:395500,payments:6,held:1,protectedCount:1}]})
  });
  await f.model.load();
  assert.equal(f.model.outcomeBars().length,5);
  assert.equal(f.model.outcomeBars()[0].value,1);
  assert.equal(f.model.settleRate(),'50%');
  assert.ok(f.model.dailyBars().length>=2);
  assert.equal(f.model.pendingHolds().length,1);
  assert.equal(f.model.heldAmount(),105000);
  assert.equal(f.model.ledger().length,1);
  assert.equal(f.model.people().length,1);
  assert.equal(f.model.riskBars().find(item=>item.label==='Very high').value,1);
  assert.doesNotMatch(html,/Daily payment value|High risk activity|dailyAmounts/);
  assert.equal('dailyAmounts' in f.model,false);
  assert.equal(f.model.holdAmountBands()[0].value,1);
  assert.equal(f.model.holdAmountBands()[1].value,0);
});

test('all admin tables have captions, column scopes and keyboard-scrollable regions',()=>{
  const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/admin.html'),'utf8');
  const tables=html.match(/<table\b[\s\S]*?<\/table>/g);
  assert.equal(tables.length,5);
  for(const table of tables) {
    assert.match(table,/<caption/);
    assert.match(table,/<th scope="col"/);
    assert.match(table,/<th scope="row"/);
  }
  assert.equal((html.match(/class="admin-table-scroll" tabindex="0" role="region"/g)||[]).length,5);
  assert.match(html,/admin-daily-table/);
  assert.match(html,/admin-panel-head[\s\S]*View users &amp; accounts/);
});

test('admin navigation keeps the same labels and order with exactly one current page',()=>{
  const html=fs.readFileSync(path.join(__dirname,'../src/ts/views/admin.html'),'utf8');
  const navs=html.match(/<nav class="admin-nav"[^>]*>[\s\S]*?<\/nav>/g) || [];
  const expectedLinks=[
    {href:'/admin/dashboard',label:'Dashboard'},
    {href:'/admin/holds',label:'Held payments'},
    {href:'/admin/users',label:'Users &amp; accounts'}
  ];
  const currentPages=['/admin/users','/admin/holds','/admin/dashboard'];
  assert.equal(navs.length,currentPages.length);
  navs.forEach((nav,index)=>{
    assert.match(nav,/aria-label="Administration"/);
    const links=[...nav.matchAll(/<a\b([^>]*)>([^<]*)<\/a>/g)].map(([,attrs,label])=>({
      href:attrs.match(/href="([^"]+)"/)[1],label:label.trim(),current:/aria-current="page"/.test(attrs)
    }));
    assert.deepEqual(links.map(({href,label})=>({href,label})),expectedLinks);
    assert.deepEqual(links.filter(link=>link.current).map(link=>link.href),[currentPages[index]]);
  });
});
