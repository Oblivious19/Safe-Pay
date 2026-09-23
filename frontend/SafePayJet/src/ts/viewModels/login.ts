import * as ko from 'knockout';
import { login as signIn } from '../services/authService';
import { landing } from '../services/sessionRouteService';
import { ApiError, errorText } from '../services/apiError';
import 'ojs/ojbutton';
class Login {
 mode=ko.observable('email');phone=ko.observable('');email=ko.observable('');password=ko.observable('');showPassword=ko.observable(false);
 phoneError=ko.observable('');emailError=ko.observable('');passwordError=ko.observable('');error=ko.observable('');submitting=ko.observable(false);private alive=true;
 togglePassword=()=>this.showPassword(!this.showPassword());
 switchMode=()=>{this.mode(this.mode()==='email'?'phone':'email');this.password('');this.error('');};
 login=async()=>{if(this.submitting())return;this.error('');this.phoneError('');this.emailError('');this.passwordError('');
 const identifier=this.mode()==='email'?this.email().trim().toLowerCase():'+91'+this.phone().trim();
 if(this.mode()==='email'&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(identifier))this.emailError('Enter a valid email.');
 if(this.mode()==='phone'&&!/^[6-9][0-9]{9}$/.test(this.phone().trim()))this.phoneError('Enter a 10-digit Indian mobile number.');
 if(!this.password()||new TextEncoder().encode(this.password()).length>72)this.passwordError('Enter your password, at most 72 UTF-8 bytes.');
 if(this.emailError()||this.phoneError()||this.passwordError())return;
 this.submitting(true);try{await signIn(identifier,this.password());if(this.alive)location.assign('/'+landing());}catch(e){if(this.alive)this.error(e instanceof ApiError&&[401,403].includes(e.status)?'Email or mobile number, or password is incorrect.':errorText(e));}finally{this.password('');this.showPassword(false);if(this.alive)this.submitting(false);}};
 connected(){document.title='Login | SafePay';if(new URLSearchParams(location.search).get('reason')==='session-expired')this.error('Your session ended. Please sign in again.');}
 disconnected(){this.alive=false;this.password('');}
}
export = Login;
