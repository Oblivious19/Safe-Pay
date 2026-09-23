import * as ko from 'knockout';
import {beneficiaries as api} from '../services/beneficiaryService';
import {Beneficiary,BeneficiaryDraft} from '../services/types';
import {ApiError,errorText} from '../services/apiError';
import {confirmAction} from '../pageModel';

enum IndianBankName {
 AXIS_BANK='Axis Bank',
 BANK_OF_BARODA='Bank of Baroda',
 BANK_OF_INDIA='Bank of India',
 CANARA_BANK='Canara Bank',
 CENTRAL_BANK_OF_INDIA='Central Bank of India',
 HDFC_BANK='HDFC Bank',
 ICICI_BANK='ICICI Bank',
 IDFC_FIRST_BANK='IDFC FIRST Bank',
 INDIAN_BANK='Indian Bank',
 INDUSIND_BANK='IndusInd Bank',
 KOTAK_MAHINDRA_BANK='Kotak Mahindra Bank',
 PUNJAB_NATIONAL_BANK='Punjab National Bank',
 STATE_BANK_OF_INDIA='State Bank of India',
 UNION_BANK_OF_INDIA='Union Bank of India',
 YES_BANK='Yes Bank'
}

class Beneficiaries {
 mode=ko.observable('list');rows=ko.observableArray<Beneficiary>([]);includeInactive=ko.observable(false);beneficiaries=ko.pureComputed(()=>this.rows().filter(x=>this.includeInactive()||x.status==='ACTIVE'));
 selected=ko.observable<Beneficiary|null>(null);beneficiaryName=ko.observable('');nickname=ko.observable('');paymentMethod=ko.observable('BANK_ACCOUNT');bankName=ko.observable('');bankAccountNumber=ko.observable('');ifsc=ko.observable('');upiId=ko.observable('');relationshipLabel=ko.observable('');purposeNote=ko.observable('');externalDetailsConfirmed=ko.observable(false);externalConfirmationRequired=ko.observable(false);
 bankOptions=Object.values(IndianBankName);
 fieldErrors=ko.observable<Record<string,string>>({});loading=ko.observable(false);saving=ko.observable(false);error=ko.observable('');success=ko.observable('');confirmDeactivate=ko.observable(false);uncertain=ko.observable(false);private alive=true;private generation=0;
 mask=(v:string)=>v||'Unavailable';added=(v:string)=>'Added '+new Date(v).toLocaleDateString('en-IN');isNew=(v:string)=>Date.now()-Date.parse(v)<86400000;
 load=async()=>{if(this.loading())return;this.loading(true);this.error('');const n=++this.generation;try{const rows=await api.list();if(this.alive&&n===this.generation)this.rows(rows);}catch(e){if(this.alive)this.error(errorText(e));}finally{if(this.alive)this.loading(false);}};
 private clear(){this.beneficiaryName('');this.nickname('');this.bankName('');this.bankAccountNumber('');this.ifsc('');this.upiId('');this.relationshipLabel('');this.purposeNote('');this.externalDetailsConfirmed(false);this.externalConfirmationRequired(false);this.fieldErrors({});}
 openAdd=()=>{if(this.saving())return;this.clear();this.error('');this.success('');this.mode('add');};
 back=()=>{if(this.saving())return;this.mode('list');this.selected(null);this.confirmDeactivate(false);};
 save=async()=>{if(this.saving()||this.uncertain())return;this.error('');this.fieldErrors({});const e:Record<string,string>={};
 const b:BeneficiaryDraft={beneficiaryName:this.beneficiaryName().trim(),paymentMethod:this.paymentMethod() as 'BANK_ACCOUNT'|'UPI'};
 if(b.beneficiaryName.length<2||b.beneficiaryName.length>120)e.beneficiaryName='Use 2–120 characters.';
 if(b.paymentMethod==='BANK_ACCOUNT'){b.bankName=this.bankName().trim();b.bankAccountNumber=this.bankAccountNumber().trim();b.ifscCode=this.ifsc().trim().toUpperCase();b.externalDetailsConfirmed=this.externalDetailsConfirmed();if(!b.bankName||b.bankName.length>120)e.bankName='Bank name is required, up to 120 characters.';if(!b.bankAccountNumber||b.bankAccountNumber.length>34)e.bankAccountNumber='Account number is required, up to 34 characters.';if(!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(b.ifscCode))e.ifsc='Enter a valid IFSC.';if(this.externalConfirmationRequired()&&!b.externalDetailsConfirmed)e.externalDetailsConfirmed='Confirm that you have verified these bank details.';}
 else{b.upiId=this.upiId().trim().toLowerCase();if(!b.upiId||b.upiId.length>255)e.upiId='UPI ID is required, up to 255 characters.';}
 const optional=(key:'nickname'|'relationshipLabel'|'purposeNote',value:string,max:number)=>{if(value.trim().length>max)e[key]='Maximum '+max+' characters.';else if(value.trim())b[key]=value.trim();};
 optional('nickname',this.nickname(),60);optional('relationshipLabel',this.relationshipLabel(),50);optional('purposeNote',this.purposeNote(),140);
 this.fieldErrors(e);if(Object.keys(e).length)return;this.saving(true);
 try{await api.create(b);if(!this.alive)return;this.clear();this.mode('list');this.success('Beneficiary added.');await this.load();}
 catch(x){if(x instanceof ApiError&&x.uncertain){this.uncertain(true);await this.load();}if(this.alive&&x instanceof ApiError&&x.code==='EXTERNAL_DETAILS_CONFIRMATION_REQUIRED'){this.externalConfirmationRequired(true);this.error('This recipient is outside SafePay. Confirm the verified bank details to save them as an external beneficiary.');}else if(this.alive)this.error(x instanceof ApiError&&x.code==='SAFE_PAY_RECIPIENT_NOT_FOUND'?'This SafePay account exists, but its details do not match.':errorText(x));}finally{if(this.alive)this.saving(false);}};
 select=async(row:Beneficiary)=>{if(this.loading()||this.saving())return;this.loading(true);this.error('');try{const d=await api.get(row.beneficiaryId);if(this.alive){this.selected(d);this.confirmDeactivate(false);this.mode('details');}}catch(e){this.error(errorText(e));}finally{this.loading(false);}};
 private async status(target:'ACTIVE'|'DISABLED'){const row=this.selected();if(!row||this.saving())return;this.saving(true);this.error('');try{await api.status(row.beneficiaryId,target);if(this.alive){this.selected(null);this.mode('list');this.success('Beneficiary status updated.');await this.load();}}catch(e){if(this.alive)this.error(errorText(e));}finally{this.saving(false);}}
 deactivate=()=>this.confirmDeactivate()?this.status('DISABLED'):Promise.resolve();
 reactivate=async()=>{if(await confirmAction('Activate beneficiary?','This recipient will become available for new payments.','Activate'))await this.status('ACTIVE');};
 acknowledge=async()=>{if(await confirmAction('Review the directory first','Only continue if you verified that the earlier beneficiary was not created.','I checked the directory'))this.uncertain(false);};
 connected(){document.title='Beneficiaries | SafePay';if(new URLSearchParams(location.search).get('action')==='add')this.openAdd();void this.load();}
 disconnected(){this.alive=false;this.generation++;}
}
export = Beneficiaries;
