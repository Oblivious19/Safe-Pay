import { request } from './apiClient';
import { Profile } from './types';
export const profile = () => request<Profile>('/users/me');
