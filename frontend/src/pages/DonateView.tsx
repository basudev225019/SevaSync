import { useNavigate } from 'react-router';
import { Clock3, ShieldCheck, Sparkles, Thermometer } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { Card, CardHeader } from '../components/ui/Card';
import { BatchForm } from '../components/modals/CreateBatchModal';
import { roleColor } from '../lib/theme';

const RULES = [
  { icon: <Thermometer className="size-4" />, title: 'Category limits', text: 'Cooked meals 4 h, beverages 8 h, bakery 12 h, fruit & veg 24 h, packaged 48 h after preparation.' },
  { icon: <Clock3 className="size-4" />, title: 'At least 30 minutes left', text: 'Anything closer to expiry cannot reach anyone safely, so it is not accepted.' },
  { icon: <Sparkles className="size-4" />, title: 'Matched automatically', text: 'Within seconds your food is split across the shelters that need it most and can be reached in time.' },
  { icon: <ShieldCheck className="size-4" />, title: 'Never handed over late', text: 'If food expires on the way, the hand-over is blocked and recorded on the audit trail.' },
];

export default function DonateView() {
  const navigate = useNavigate();
  return (
    <>
      <PageHeader
        eyebrow="Log surplus"
        accent={roleColor.DONOR}
        title="What's left over?"
        description="Thirty seconds now saves a meal later. The safety check runs as you type."
      />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <BatchForm onDone={() => navigate('/app/batches')} />
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader title="Food safety rules" subtitle="Enforced by the server for every batch" icon={<ShieldCheck className="size-4" />} />
          <ul className="space-y-4">
            {RULES.map((r) => (
              <li key={r.title} className="flex gap-3">
                <span className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-xl bg-wash/5 text-saffron-600">{r.icon}</span>
                <div>
                  <p className="text-sm font-semibold text-ink-100">{r.title}</p>
                  <p className="mt-0.5 text-sm text-ink-400">{r.text}</p>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </>
  );
}
