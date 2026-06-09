import { redirect } from 'next/navigation';

export default function TutorRedirectPage() {
  redirect('/dashboard/agent');
}
