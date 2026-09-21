import React, { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Upload, FileSpreadsheet, FileText, Download, AlertCircle, CheckCircle, X } from 'lucide-react';
import { apiClient, IMPORT_API_URL } from '@/api/client';
import { useTeams } from '@/hooks/useTeams';
import { Team } from '@/types';

interface TaskImportResponse {
  totalRows: number;
  successCount: number;
  failureCount: number;
  errors: string[];
  importedTasks: any[];
  message: string;
}

interface TaskImportProps {
  teamId?: string;
  onImportSuccess?: (result: TaskImportResponse) => void;
}

const TaskImport: React.FC<TaskImportProps> = ({ teamId, onImportSuccess }) => {
  const queryClient = useQueryClient();
  const { data: teamsPage } = useTeams(0, 50);
  const teams = teamsPage?.content || [];
  const [importing, setImporting] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [importResult, setImportResult] = useState<TaskImportResponse | null>(null);
  const [dragActive, setDragActive] = useState(false);
  const [attendanceMode, setAttendanceMode] = useState(false);

  // Default import date to today
  const todayStr = () => {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  };
  const [importDate, setImportDate] = useState(todayStr());

  const handleFileUpload = async (file: File | null) => {
    if (!file) return;

    // Validate file type
    const validExtensions = ['.xlsx', '.docx'];
    const fileExtension = file.name.toLowerCase().slice(file.name.lastIndexOf('.'));
    
    if (!validExtensions.includes(fileExtension)) {
      alert('Invalid file type. Please upload .xlsx or .docx files only.');
      return;
    }

    const isExcel = file.name.toLowerCase().endsWith('.xlsx');
    // Attendance tasksheet parsing only applies to Excel files.
    const endpoint = attendanceMode && isExcel
      ? 'attendance'
      : (isExcel ? 'excel' : 'word');
    setImporting(true);
    setImportResult(null);

    try {
      const formData = new FormData();
      formData.append('file', file);

      let resolvedTeamId = teamId || teams[0]?.id;
      if (!resolvedTeamId) {
        const created = await apiClient.post<Team>('/teams', {
          name: 'ASE',
          description: 'Department: ASE',
        });
        resolvedTeamId = created.data.id;
        await queryClient.invalidateQueries({ queryKey: ['teams'] });
      }

      const response = await apiClient.post<TaskImportResponse>(
        `/import/tasks/${endpoint}/${resolvedTeamId}${attendanceMode && importDate ? `?importDate=${importDate}` : ''}`,
        formData,
        { baseURL: IMPORT_API_URL, timeout: 300000 }
      );

      const result = response.data;
      setImportResult(result);
      
      if (result.successCount > 0) {
        queryClient.invalidateQueries({ queryKey: ['attendance'] });
        onImportSuccess?.(result);
      }
    } catch (error: any) {
      console.error('Import failed:', error);
      const status = error.response?.status;
      const rateLimited =
        status === 429
          ? 'Too many requests (HTTP 429). Wait 1–2 minutes, then try once.'
          : status === 503
            ? 'The live API is waking up or restarting (HTTP 503). Open https://dinacharya-ese5.onrender.com/api/v1/actuator/health, wait until it shows UP, then try again.'
          : null;
      setImportResult({
        totalRows: 0,
        successCount: 0,
        failureCount: 1,
        errors: [
          rateLimited ||
            error.response?.data?.detail ||
            error.response?.data?.message ||
            'Import failed: ' + error.message,
        ],
        importedTasks: [],
        message: 'Import failed'
      });
    } finally {
      setImporting(false);
      setShowModal(true);
    }
  };

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] || null;
    handleFileUpload(file);
    e.target.value = ''; // Reset input
  };

  const handleDrag = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFileUpload(e.dataTransfer.files[0]);
    }
  };

  const downloadTemplate = async () => {
    try {
      const response = await apiClient.get('/import/template/excel', {
        responseType: 'blob'
      });
      
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'task-import-template.csv');
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      console.error('Failed to download template:', error);
      alert('Failed to download template');
    }
  };

  const closeModal = () => {
    setShowModal(false);
    setImportResult(null);
  };

  return (
    <>
      <div className="task-import-container">
        <div className="flex gap-3 items-center flex-wrap">
          {/* Upload Button */}
          <label className={`btn btn-primary flex items-center gap-2 ${importing ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}>
            <Upload className="w-4 h-4" />
            {importing ? 'Importing...' : 'Import Tasks'}
            <input
              type="file"
              accept=".xlsx,.docx"
              onChange={handleFileInput}
              disabled={importing}
              className="hidden"
            />
          </label>

          {/* Download Template */}
          <button
            onClick={downloadTemplate}
            className="btn btn-outline flex items-center gap-2"
            title="Download CSV Template"
            type="button"
          >
            <Download className="w-4 h-4" />
            Template
          </button>

          {/* Attendance format toggle */}
          <label className="flex items-center gap-2 text-sm text-charcoal cursor-pointer select-none px-3 py-2 rounded-lg border border-warm-border bg-white hover:bg-sand/40 transition-colors">
            <input
              type="checkbox"
              checked={attendanceMode}
              onChange={(e) => setAttendanceMode(e.target.checked)}
              disabled={importing}
              className="accent-terracotta w-4 h-4"
            />
            <span className="material-symbols-outlined text-[16px] text-terracotta">event_note</span>
            Attendance tasksheet
          </label>

          {/* Date filter — only shown in attendance mode */}
          {attendanceMode && (
            <div className="flex items-center gap-2 px-3 py-2 rounded-lg border border-terracotta/30 bg-terracotta/5">
              <span className="material-symbols-outlined text-[16px] text-terracotta">calendar_today</span>
              <label htmlFor="import-date" className="text-sm font-medium text-charcoal whitespace-nowrap">
                Import date
              </label>
              <input
                id="import-date"
                type="date"
                value={importDate}
                onChange={(e) => setImportDate(e.target.value)}
                disabled={importing}
                className="border border-warm-border rounded-lg px-2 py-1 text-sm text-charcoal bg-white focus:outline-none focus:ring-2 focus:ring-terracotta/40"
              />
              {importDate ? (
                <button
                  type="button"
                  onClick={() => setImportDate('')}
                  className="text-xs text-charcoal-muted hover:text-terracotta underline whitespace-nowrap transition-colors"
                  title="Remove date filter — import all rows"
                >
                  All dates
                </button>
              ) : (
                <span className="text-xs text-amber-600 font-medium flex items-center gap-1">
                  <span className="material-symbols-outlined text-[14px]">warning</span>
                  Imports all rows
                </span>
              )}
            </div>
          )}

          {/* Info */}
          {attendanceMode && importDate && (
            <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[#22c55e]/10 text-[#16a34a] text-xs font-medium">
              <span className="material-symbols-outlined text-[14px]">check_circle</span>
              Importing {importDate} only
            </div>
          )}
          {!attendanceMode && (
            <span className="text-sm text-charcoal-muted">Supported: .xlsx, .docx</span>
          )}
        </div>

        {/* Drag & Drop Zone */}
        <div
          className={`mt-4 border-2 border-dashed rounded-lg p-8 text-center transition-colors ${
            dragActive ? 'border-primary bg-primary/5' : 'border-gray-300 bg-gray-50'
          } ${importing ? 'opacity-50 pointer-events-none' : 'cursor-pointer hover:border-primary/60'}`}
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
          onClick={() => document.getElementById('file-input-drop')?.click()}
        >
          <input
            id="file-input-drop"
            type="file"
            accept=".xlsx,.docx"
            onChange={handleFileInput}
            disabled={importing}
            className="hidden"
          />
          
          <div className="flex flex-col items-center gap-3">
            <div className="flex gap-4">
              <FileSpreadsheet className="w-12 h-12 text-green-600" />
              <FileText className="w-12 h-12 text-blue-600" />
            </div>
            <p className="text-lg font-medium text-charcoal">
              {dragActive ? 'Drop file here' : 'Drag & drop or click to upload'}
            </p>
            <p className="text-sm text-charcoal-muted">
              Excel (.xlsx) or Word (.docx) files
            </p>
          </div>
        </div>
      </div>

      {/* Import Result Modal */}
      {showModal && importResult && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[90vh] overflow-hidden">
            {/* Header */}
            <div className={`p-6 border-b ${
              importResult.failureCount === 0 ? 'bg-green-50' : 
              importResult.successCount === 0 ? 'bg-red-50' : 'bg-yellow-50'
            }`}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  {importResult.failureCount === 0 ? (
                    <CheckCircle className="w-8 h-8 text-green-600" />
                  ) : (
                    <AlertCircle className="w-8 h-8 text-yellow-600" />
                  )}
                  <div>
                    <h3 className="text-xl font-bold text-gray-900">Import Complete</h3>
                    <p className="text-sm text-gray-600">{importResult.message}</p>
                  </div>
                </div>
                <button
                  onClick={closeModal}
                  className="text-gray-400 hover:text-gray-600"
                  type="button"
                >
                  <X className="w-6 h-6" />
                </button>
              </div>
            </div>

            {/* Stats */}
            <div className="p-6 border-b bg-gray-50">
              <div className="grid grid-cols-3 gap-4">
                <div className="text-center">
                  <div className="text-3xl font-bold text-gray-700">
                    {importResult.totalRows}
                  </div>
                  <div className="text-sm text-gray-500">Total Rows</div>
                </div>
                <div className="text-center">
                  <div className="text-3xl font-bold text-green-600">
                    {importResult.successCount}
                  </div>
                  <div className="text-sm text-gray-500">Succeeded</div>
                </div>
                <div className="text-center">
                  <div className="text-3xl font-bold text-red-600">
                    {importResult.failureCount}
                  </div>
                  <div className="text-sm text-gray-500">Failed</div>
                </div>
              </div>
            </div>

            {/* Content */}
            <div className="p-6 overflow-y-auto" style={{ maxHeight: '400px' }}>
              {/* Errors */}
              {importResult.errors && importResult.errors.length > 0 && (
                <div className="mb-4">
                  <h4 className="font-semibold text-red-600 mb-2 flex items-center gap-2">
                    <AlertCircle className="w-5 h-5" />
                    Errors ({importResult.errors.length})
                  </h4>
                  <div className="bg-red-50 border border-red-200 rounded-lg p-3">
                    <ul className="list-disc list-inside space-y-1 text-sm text-red-700">
                      {importResult.errors.map((error, index) => (
                        <li key={index}>{error}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              )}

              {/* Successfully Imported Tasks */}
              {importResult.importedTasks && importResult.importedTasks.length > 0 && (
                <div>
                  <h4 className="font-semibold text-green-600 mb-2 flex items-center gap-2">
                    <CheckCircle className="w-5 h-5" />
                    Successfully Imported ({importResult.importedTasks.length})
                  </h4>
                  <div className="space-y-2">
                    {importResult.importedTasks.slice(0, 10).map((task, index) => (
                      <div
                        key={index}
                        className="bg-green-50 border border-green-200 rounded-lg p-3"
                      >
                        <div className="flex items-start justify-between">
                          <div className="flex-1">
                            <div className="font-medium text-gray-900">{task.title}</div>
                            {task.description && (
                              <div className="text-sm text-gray-600 mt-1">
                                {task.description.substring(0, 100)}
                                {task.description.length > 100 ? '...' : ''}
                              </div>
                            )}
                          </div>
                          <div className="flex gap-2 ml-3">
                            <span className={`px-2 py-1 text-xs rounded ${
                              task.priority === 'URGENT' || task.priority === 'HIGH' ? 'bg-red-100 text-red-700' :
                              task.priority === 'MEDIUM' ? 'bg-yellow-100 text-yellow-700' :
                              'bg-green-100 text-green-700'
                            }`}>
                              {task.priority}
                            </span>
                            <span className={`px-2 py-1 text-xs rounded ${
                              task.status === 'DONE' ? 'bg-green-100 text-green-700' :
                              task.status === 'IN_PROGRESS' ? 'bg-blue-100 text-blue-700' :
                              task.status === 'IN_REVIEW' ? 'bg-purple-100 text-purple-700' :
                              'bg-gray-100 text-gray-700'
                            }`}>
                              {task.status}
                            </span>
                          </div>
                        </div>
                      </div>
                    ))}
                    {importResult.importedTasks.length > 10 && (
                      <div className="text-sm text-gray-500 text-center py-2">
                        ... and {importResult.importedTasks.length - 10} more tasks
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* Footer */}
            <div className="p-6 border-t bg-gray-50 flex justify-end gap-3">
              <button
                onClick={closeModal}
                className="btn btn-primary"
                type="button"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default TaskImport;
