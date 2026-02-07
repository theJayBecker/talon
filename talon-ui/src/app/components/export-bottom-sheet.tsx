import { X, Copy, Check } from "lucide-react";
import { Trip } from "../lib/trip-types";
import { generateTripMarkdown, generateTripFilename, ExportOptions } from "../lib/trip-markdown";
import { useState } from "react";

interface ExportBottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  trip: Trip;
}

export function ExportBottomSheet({ isOpen, onClose, trip }: ExportBottomSheetProps) {
  const [options, setOptions] = useState<ExportOptions>({
    includeFrontmatter: true,
    includeSummary: true,
    includeSamples: false,
  });
  const [notes, setNotes] = useState(trip.notes || "");
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const filename = generateTripFilename(trip);

  const handleCopy = async () => {
    const markdown = generateTripMarkdown(trip, { ...options, notes });
    
    try {
      await navigator.clipboard.writeText(markdown);
      setCopied(true);
      setTimeout(() => {
        setCopied(false);
        onClose();
      }, 1500);
    } catch (err) {
      console.error("Failed to copy:", err);
    }
  };

  const toggleOption = (key: keyof ExportOptions) => {
    setOptions((prev) => ({
      ...prev,
      [key]: !prev[key],
    }));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/60" onClick={onClose} />

      {/* Bottom Sheet */}
      <div className="relative bg-card border border-border rounded-t-2xl sm:rounded-2xl w-full sm:max-w-md max-h-[90vh] overflow-auto animate-in slide-in-from-bottom duration-200 sm:slide-in-from-bottom-0">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-border sticky top-0 bg-card z-10">
          <h3 className="text-lg font-semibold text-foreground">Export Trip</h3>
          <button
            onClick={onClose}
            className="p-2 hover:bg-muted rounded-lg transition-colors"
          >
            <X className="w-5 h-5 text-muted-foreground" />
          </button>
        </div>

        {/* Content */}
        <div className="p-4 space-y-4">
          {/* Filename */}
          <div>
            <label className="text-xs text-muted-foreground uppercase tracking-wide block mb-2">
              File Name
            </label>
            <div className="bg-muted/30 border border-border rounded-lg px-3 py-2 text-sm text-foreground font-mono">
              {filename}
            </div>
          </div>

          {/* Options */}
          <div>
            <label className="text-xs text-muted-foreground uppercase tracking-wide block mb-2">
              Export Options
            </label>
            <div className="space-y-2">
              <label className="flex items-center justify-between p-3 bg-muted/30 border border-border rounded-lg cursor-pointer hover:bg-muted/50 transition-colors">
                <span className="text-sm text-foreground">Include YAML Frontmatter</span>
                <input
                  type="checkbox"
                  checked={options.includeFrontmatter}
                  onChange={() => toggleOption("includeFrontmatter")}
                  className="w-5 h-5 rounded border-2 border-border bg-background checked:bg-primary checked:border-primary cursor-pointer accent-primary"
                />
              </label>
              <label className="flex items-center justify-between p-3 bg-muted/30 border border-border rounded-lg cursor-pointer hover:bg-muted/50 transition-colors">
                <span className="text-sm text-foreground">Include Summary Table</span>
                <input
                  type="checkbox"
                  checked={options.includeSummary}
                  onChange={() => toggleOption("includeSummary")}
                  className="w-5 h-5 rounded border-2 border-border bg-background checked:bg-primary checked:border-primary cursor-pointer accent-primary"
                />
              </label>
              <label className="flex items-center justify-between p-3 bg-muted/30 border border-border rounded-lg cursor-pointer hover:bg-muted/50 transition-colors">
                <span className="text-sm text-foreground">Include Sample Data</span>
                <input
                  type="checkbox"
                  checked={options.includeSamples}
                  onChange={() => toggleOption("includeSamples")}
                  className="w-5 h-5 rounded border-2 border-border bg-background checked:bg-primary checked:border-primary cursor-pointer accent-primary"
                />
              </label>
            </div>
          </div>

          {/* Notes */}
          <div>
            <label className="text-xs text-muted-foreground uppercase tracking-wide block mb-2">
              Notes (Optional)
            </label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Add notes to include in the exported file..."
              className="w-full bg-muted/30 border border-border rounded-lg px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/50 resize-none"
              rows={3}
            />
          </div>

          {/* Preview Info */}
          <div className="bg-primary/10 border border-primary/30 rounded-lg p-3 text-xs text-muted-foreground">
            <p>
              The markdown file will be copied to your clipboard. You can then paste it into any
              text editor or markdown viewer.
            </p>
          </div>
        </div>

        {/* Actions */}
        <div className="p-4 border-t border-border sticky bottom-0 bg-card">
          <button
            onClick={handleCopy}
            disabled={copied}
            className="w-full flex items-center justify-center gap-2 bg-primary text-primary-foreground px-4 py-3 rounded-lg font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {copied ? (
              <>
                <Check className="w-4 h-4" />
                Copied to Clipboard!
              </>
            ) : (
              <>
                <Copy className="w-4 h-4" />
                Copy to Clipboard
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
