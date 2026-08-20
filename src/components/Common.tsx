import React from 'react';
import { CheckCircle2, AlertTriangle, X } from 'lucide-react';

interface RequirementRowProps {
  title: string;
  description: string;
  satisfied: boolean;
  actionLabel: string;
  onAction: () => void;
}

export const RequirementRow: React.FC<RequirementRowProps> = ({
  title,
  description,
  satisfied,
  actionLabel,
  onAction,
}) => {
  return (
    <div
      className={`w-full rounded-xl p-3.5 transition-colors border ${
        satisfied
          ? 'bg-[#2B2930] border-transparent'
          : 'bg-[#8C1D18]/25 border-[#F2B8B5]/30'
      }`}
    >
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3 flex-1 min-w-0">
          {satisfied ? (
            <CheckCircle2 className="w-5 h-5 text-[#D0BCFF] shrink-0" />
          ) : (
            <AlertTriangle className="w-5 h-5 text-[#F2B8B5] shrink-0" />
          )}
          <div className="min-w-0">
            <div className="text-[15px] font-medium text-[#E6E1E5]">{title}</div>
            <div className="text-xs text-[#CAC4D0] mt-0.5 leading-snug">{description}</div>
          </div>
        </div>

        {!satisfied && (
          <button
            onClick={onAction}
            className="shrink-0 px-3.5 py-1.5 rounded-lg border border-[#D0BCFF] text-[#D0BCFF] text-xs font-medium hover:bg-[#D0BCFF]/10 active:bg-[#D0BCFF]/20 transition-colors"
          >
            {actionLabel}
          </button>
        )}
      </div>
    </div>
  );
};

interface ChipProps {
  text: string;
  color?: string;
  bgColor?: string;
}

export const Chip: React.FC<ChipProps> = ({
  text,
  color = '#D0BCFF',
  bgColor,
}) => {
  return (
    <span
      className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono font-medium tracking-tight"
      style={{
        color: color,
        backgroundColor: bgColor || `${color}25`,
        borderColor: `${color}60`,
        borderWidth: '1px',
      }}
    >
      {text}
    </span>
  );
};

export const StatusDot: React.FC<{ color?: string; size?: number }> = ({
  color = '#00BFA5',
  size = 8,
}) => {
  return (
    <span
      className="inline-block rounded-full shrink-0"
      style={{
        width: `${size}px`,
        height: `${size}px`,
        backgroundColor: color,
      }}
    />
  );
};

export const SectionHeader: React.FC<{ text: string; className?: string }> = ({
  text,
  className = '',
}) => {
  return (
    <div
      className={`text-[11px] uppercase tracking-wider font-semibold text-[#D0BCFF] pt-3 pb-1 ${className}`}
    >
      {text}
    </div>
  );
};

export const EmptyState: React.FC<{ title: string; subtitle: string }> = ({
  title,
  subtitle,
}) => {
  return (
    <div className="w-full flex flex-col items-center justify-center py-10 px-4 text-center rounded-xl bg-[#2B2930]/40 border border-white/5 my-2">
      <div className="text-[15px] font-medium text-[#E6E1E5]">{title}</div>
      <div className="text-xs text-[#CAC4D0] mt-1 max-w-sm leading-relaxed">{subtitle}</div>
    </div>
  );
};

interface TextInputDialogProps {
  title: string;
  label: string;
  initial?: string;
  confirmLabel?: string;
  onDismiss: () => void;
  onConfirm: (val: string) => void;
}

export const TextInputDialog: React.FC<TextInputDialogProps> = ({
  title,
  label,
  initial = '',
  confirmLabel = 'OK',
  onDismiss,
  onConfirm,
}) => {
  const [value, setValue] = React.useState(initial);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (value.trim()) {
      onConfirm(value.trim());
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
      <div className="w-full max-w-md bg-[#2B2930] rounded-2xl p-6 shadow-2xl border border-white/10 text-[#E6E1E5]">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-medium">{title}</h3>
          <button onClick={onDismiss} className="text-[#CAC4D0] hover:text-white p-1">
            <X className="w-5 h-5" />
          </button>
        </div>
        <form onSubmit={handleSubmit}>
          <label className="block text-xs font-medium text-[#CAC4D0] mb-1.5">{label}</label>
          <input
            type="text"
            autoFocus
            value={value}
            onChange={(e) => setValue(e.target.value)}
            className="w-full bg-[#1C1B1F] border border-[#938F99] rounded-lg px-3.5 py-2.5 text-sm text-white focus:outline-hidden focus:border-[#D0BCFF] mb-6"
            placeholder={label}
          />
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onDismiss}
              className="px-4 py-2 rounded-lg text-sm text-[#D0BCFF] hover:bg-[#D0BCFF]/10 font-medium"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={!value.trim()}
              className="px-4 py-2 rounded-lg text-sm bg-[#D0BCFF] text-[#381E72] hover:bg-[#EADDFF] font-medium disabled:opacity-50"
            >
              {confirmLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel?: string;
  onDismiss: () => void;
  onConfirm: () => void;
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  title,
  message,
  confirmLabel = 'Apagar',
  onDismiss,
  onConfirm,
}) => {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4">
      <div className="w-full max-w-md bg-[#2B2930] rounded-2xl p-6 shadow-2xl border border-white/10 text-[#E6E1E5]">
        <h3 className="text-lg font-medium mb-2">{title}</h3>
        <p className="text-sm text-[#CAC4D0] mb-6 leading-relaxed">{message}</p>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onDismiss}
            className="px-4 py-2 rounded-lg text-sm text-[#D0BCFF] hover:bg-[#D0BCFF]/10 font-medium"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="px-4 py-2 rounded-lg text-sm bg-[#BA1A1A] text-white hover:bg-[#93000A] font-medium"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
};
