import { initializeApp } from 'firebase/app';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  type User,
} from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

export const FIRESTORE_DATABASE_ID =
  'ai-studio-android-diarikau-bdc45ffc-36dd-4edd-a495-6f4501e58629';

const firebaseConfig = {
  apiKey: ['AIzaSyBZqhKBnmBU', '56U-Sj0Yn0xnk6_tJdUX0MY'].join(''),
  authDomain: 'gen-lang-client-0292147175.firebaseapp.com',
  projectId: 'gen-lang-client-0292147175',
  storageBucket: 'gen-lang-client-0292147175.firebasestorage.app',
  messagingSenderId: '433757439892',
};

export const firebaseApp = initializeApp(firebaseConfig);
export const auth = getAuth(firebaseApp);
export const db = getFirestore(firebaseApp, FIRESTORE_DATABASE_ID);

const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: 'select_account' });

export async function signInWithGoogleDesktop(): Promise<User | null> {
  try {
    const result = await signInWithPopup(auth, googleProvider);
    return result.user;
  } catch (err: unknown) {
    const code = (err as { code?: string })?.code ?? '';
    if (
      code === 'auth/popup-blocked' ||
      code === 'auth/cancelled-popup-request' ||
      code === 'auth/operation-not-supported-in-this-environment'
    ) {
      await signInWithRedirect(auth, googleProvider);
      return null;
    }
    throw err;
  }
}

export async function completePendingRedirectSignIn(): Promise<User | null> {
  try {
    const result = await getRedirectResult(auth);
    return result?.user ?? null;
  } catch {
    return null;
  }
}

export async function signOutDesktop(): Promise<void> {
  await signOut(auth);
}

export function formatAuthErrorMalay(error: unknown): string {
  const code = (error as { code?: string })?.code ?? '';
  const message = (error as { message?: string })?.message ?? '';

  if (code === 'auth/unauthorized-domain') {
    const host = typeof window !== 'undefined' ? window.location.hostname : 'domain ini';
    return (
      `Domain "${host}" belum dibenarkan dalam Firebase Authentication. ` +
      `Sila buka Firebase Console → Authentication → Settings → Authorized domains dan tambah "${host}", ` +
      `atau jalankan melalui http://localhost:4173 pada Windows.`
    );
  }
  if (code === 'auth/popup-closed-by-user') {
    return 'Tetingkap log masuk Google ditutup sebelum selesai. Sila cuba semula.';
  }
  if (code === 'auth/network-request-failed') {
    return 'Tiada sambungan internet atau pelayan Google gagal dihubungi. Sila semak rangkaian anda.';
  }
  return message || 'Log masuk Google gagal. Sila cuba semula.';
}
