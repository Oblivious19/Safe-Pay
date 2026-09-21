import * as ko from 'knockout';
import {register,passwordProblem} from '../services/authService';
import {ApiError,errorText} from '../services/apiError';
import 'ojs/ojbutton';
const steps=['mobile','details','password','success'];
class Register {
 step=ko.observable('mobile');phone=ko.observable('');name=ko.observable('');email=ko.observable('');password=ko.observable('');confirmPassword=ko.observable('');showPassword=ko.observable(false);
 error=ko.observable('');notice=ko.observable('');busy=ko.observable(false);userId=ko.observable('');uncertain=false;progress=ko.pureComputed(()=>Math.min(steps.indexOf(this.step())+1,3));private reached=0;private alive=true;
 constructor(private context:any={}){}
 connected(){document.title='Register | SafePay';this.parametersChanged(this.context.params||{});}
 parametersChanged(params:{step?:string}){const index=steps.indexOf(params.step||'mobile');this.step(steps[Math.max(0,Math.min(index,this.reached))]);}
 private clear(){this.password('');this.confirmPassword('');this.showPassword(false);}
 private async go(step:string){this.step(step);if(this.context.router)await this.context.router.go({path:'register',params:{step}});}
 back=()=>{if(this.busy())return;this.clear();void this.go(steps[Math.max(0,steps.indexOf(this.step())-1)]);};
 next=async()=>{if(this.busy())return;this.error('');try{
 if(this.step()==='mobile'){if(!/^[6-9][0-9]{9}$/.test(this.phone().trim()))throw Error('Enter a 10-digit Indian mobile number.');this.reached=Math.max(this.reached,1);await this.go('details');}
 else if(this.step()==='details'){if(this.name().trim().length<2||this.name().trim().length>120)throw Error('Name must be 2–120 characters.');if(this.email().trim().length>254||!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email().trim()))throw Error('Enter a valid email address.');this.reached=Math.max(this.reached,2);await this.go('password');}
 else if(this.step()==='password'){if(this.uncertain)throw Error('Registration outcome is unknown. Try signing in before registering again.');const issue=passwordProblem(this.password(),this.email(),this.name());if(issue)throw Error(issue);if(this.password()!==this.confirmPassword())throw Error('Passwords do not match.');
 this.busy(true);try{const result=await register({fullName:this.name().trim(),email:this.email().trim().toLowerCase(),mobileNumber:'+91'+this.phone().trim(),password:this.password()});if(this.alive){this.userId(result.userId);this.reached=3;await this.go('success');}}catch(e){if(e instanceof ApiError&&e.uncertain)this.uncertain=true;throw e;}finally{this.clear();this.busy(false);}}
 }catch(e){if(this.alive)this.error(errorText(e));}};
 copy=async()=>{try{await navigator.clipboard.writeText(this.userId());this.notice('Customer ID copied.');}catch{this.notice('Select and copy your Customer ID.');}};
 disconnected(){this.alive=false;this.clear();}
}
export = Register;
