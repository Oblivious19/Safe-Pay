export function money(value) {
  const text=String(value);if(!/^\d+(\.\d{1,2})?$/.test(text))return 'Amount unavailable';
  const [whole,fraction='']=text.split('.');
  return '₹'+BigInt(whole).toLocaleString('en-IN')+'.'+fraction.padEnd(2,'0');
}
export function safeAdminUrl(value) {
  try {const url=new URL(value);return url.protocol==='http:' && ['localhost','127.0.0.1'].includes(url.hostname) && !url.username && !url.password?url.href:'http://localhost:8000/admin';}
  catch{return 'http://localhost:8000/admin';}
}
