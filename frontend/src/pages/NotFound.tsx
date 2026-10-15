import { Link } from 'react-router';
import { Compass } from 'lucide-react';
import { Button } from '../components/ui/Button';
import DecryptedText from '../components/reactbits/DecryptedText';

export default function NotFound() {
  return (
    <div className="grid min-h-dvh place-items-center bg-night-900 px-6 text-center">
      <div>
        <Compass className="mx-auto size-10 text-saffron-600" />
        <h1 className="mt-6 font-display text-6xl font-semibold">
          <DecryptedText text="404" animateOn="view" speed={60} maxIterations={14} sequential />
        </h1>
        <p className="mt-3 text-ink-400">This page wandered off before it was delivered.</p>
        <Link to="/"><Button className="mt-8">Back to SevaSync</Button></Link>
      </div>
    </div>
  );
}
