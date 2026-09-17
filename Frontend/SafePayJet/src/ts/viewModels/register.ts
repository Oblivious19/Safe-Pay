import * as ko from "knockout";
import {authService} from "../services/authService";
import {ApiError} from "../services/apiError";
import "ojs/ojbutton";
const steps=["mobile","details","password","success"];
class RegisterViewModel {
 step=ko.observable("mobile");phone=ko.observable("");name=ko.observable("");email=ko.observable("");
 password=ko.observable("");confirmPassword=ko.observable("");showPassword=ko.observable(false);
 error=ko.observable("");notice=ko.observable("");busy=ko.observable(false);
 progress=ko.pureComputed(()=>Math.min(steps.indexOf(this.step())+1,3));
 private reached=0;private alive=true;
 constructor(private context:any={}){}
 connected():void{this.alive=true;this.parametersChanged(this.context.params||{});}
 parametersChanged(params:{step?:string}):void{
  const target=steps.indexOf(params.step||"mobile");
  if(this.reached===3 && target!==3){void this.go("success");return;}
  if(target<0 || target>this.reached){void this.go(steps[this.reached]);return;}
  this.step(steps[target]);this.error("");
 }
 private async go(step:string):Promise<void>{this.step(step);this.error("");if(this.context.router)await this.context.router.go({path:"register",params:{step}});document.getElementById("onboarding-title")?.focus();}
 private clearSecrets():void{this.password("");this.confirmPassword("");this.showPassword(false);}
 back=():void=>{if(this.busy())return;this.clearSecrets();void this.go(steps[Math.max(0,steps.indexOf(this.step())-1)]);};
 next=async():Promise<void>=>{
  if(this.busy())return;this.error("");
  try{
   if(this.step()==="mobile"){
    if(!/^[6-9][0-9]{9}$/.test(this.phone()))throw Error("Enter a valid 10-digit mobile number.");
    this.reached=Math.max(this.reached,1);await this.go("details");
   }else if(this.step()==="details"){
    if(!this.name().trim() || this.name().trim().length>100)throw Error("Enter a name of 1–100 characters.");
    if(this.email().trim().length>150 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email().trim()))throw Error("Enter a valid email address.");
    this.reached=Math.max(this.reached,2);await this.go("password");
   }else if(this.step()==="password"){
    if(this.reached<2)throw Error("Complete your contact details first.");
    if(this.password().trim().length<8 || new TextEncoder().encode(this.password()).length>72)throw Error("Use at least 8 characters and at most 72 UTF-8 bytes.");
    if(this.password()!==this.confirmPassword())throw Error("Passwords do not match.");
    this.busy(true);
    try{await authService.register({name:this.name().trim(),email:this.email().trim(),phone:this.phone(),password:this.password()});if(this.alive){this.reached=3;await this.go("success");}}
    finally{this.clearSecrets();this.busy(false);}
   }
  }catch(e){if(this.alive)this.error(e instanceof ApiError?e.message:(e as Error).message);}
 };
 disconnected():void{this.alive=false;this.clearSecrets();this.name("");this.email("");this.phone("");}
}
export = RegisterViewModel;
