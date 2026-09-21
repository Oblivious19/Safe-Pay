import * as ko from 'knockout';
import {request,query,RequestOptions} from './apiClient';
import {checkedPage} from './transactionService';
import {Page} from './types';
import {validId} from '../utils/money';
import {ApiError} from './apiError';
import {session} from './authService';
export type Evidence = Record<string,any>;
export const staff = {
 list: async(path:string,filters:Record<string,string|number|undefined>)=>checkedPage(await request<Page<Evidence>>(path+query(filters))),
 get:(path:string)=>request<Evidence>(path),
 user:(id:string,action:'status'|'assign'|'remove'|'revoke',value='')=>{
  const path='/admin/users/'+validId(id);
  const options:RequestOptions=action==='status'?{method:'PATCH',body:{status:value}}:action==='assign'?{method:'PUT'}:action==='remove'?{method:'DELETE'}:{method:'POST'};
  return request<Evidence>(path+(action==='status'?'/status':action==='revoke'?'/sessions/revoke':'/roles/'+encodeURIComponent(value)),options);
 }
};
const markerKey='safepay.pending-review.v1';
interface Marker {userId:string;reviewId:string;action:string;key:string;createdAt:number}
let draft:{marker:Marker;body:Readonly<Record<string,string>>}|null=null;
let sending=false;
export const reviewPending=ko.observable(false);
const actions=['approve','reject','request-verification','notes'];
export function reviewRecovery():Marker|null {
 const raw=sessionStorage.getItem(markerKey);if(!raw){reviewPending(false);return null;}
 let m:Marker;try{m=JSON.parse(raw);}catch{reviewPending(true);throw Error('Unconfirmed review metadata cannot be read. Inspect audit evidence before clearing it.');}
 if(m.userId!==session()?.userId){sessionStorage.removeItem(markerKey);draft=null;reviewPending(false);return null;}
 reviewPending(true);
 if(!actions.includes(m.action)||!m.key||!Number.isFinite(m.createdAt))throw Error('Unconfirmed review metadata is invalid. Inspect audit evidence.');
 validId(m.reviewId);return m;
}
export function clearReviewRecovery(){sessionStorage.removeItem(markerKey);draft=null;reviewPending(false);}
export function canReplayReview(){try{const m=reviewRecovery();return !!m&&!!draft&&draft.marker.key===m.key&&Date.now()-m.createdAt<11*3600000;}catch{return false;}}
export async function decide(reviewId?:string,action?:string,text=''):Promise<Evidence>{
 if(sending)throw Error('A review action is already running.');
 const userId=session()?.userId;if(!userId)throw Error('Please sign in.');
 const pending=reviewRecovery();
 if(action){
  if(pending)throw Error('Resolve the previous review action first.');
  if(!actions.includes(action))throw Error('Invalid review action.');
  const value=text.trim();if(action!=='approve'&&!value)throw Error(action==='notes'?'Enter a note.':'Enter a decision reason.');
  if(value.length>1000)throw Error('Use at most 1,000 characters.');
  const marker={userId,reviewId:validId(reviewId),action,key:crypto.randomUUID(),createdAt:Date.now()};
  const body:Readonly<Record<string,string>>=Object.freeze(action==='notes'?{note:value} as Record<string,string>:{reason:value});
  sessionStorage.setItem(markerKey,JSON.stringify(marker));draft={marker,body};reviewPending(true);
 }
 if(!canReplayReview()||!draft)throw Error('The original request is unavailable or too old to replay. Inspect the review and audit timeline before clearing recovery.');
 const saved=draft;sending=true;
 try{
  const result=await request<Evidence>('/admin/risk-reviews/'+saved.marker.reviewId+'/'+saved.marker.action,{method:'POST',key:saved.marker.key,body:saved.body});
  if(session()?.userId===userId)clearReviewRecovery();window.dispatchEvent(new Event('safepay:refresh'));return result;
 }catch(e){if(e instanceof ApiError&&!e.uncertain&&session()?.userId===userId)clearReviewRecovery();throw e;}
 finally{sending=false;}
}
window.addEventListener('beforeunload',event=>{if(reviewPending()){event.preventDefault();event.returnValue='';}});
session.subscribe(value=>{if(!value){clearReviewRecovery();}else if(draft&&draft.marker.userId!==value.userId){clearReviewRecovery();}});
