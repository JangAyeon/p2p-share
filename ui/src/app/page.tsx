'use client';

import { useState } from 'react';
import FileUpload from '@/components/FileUpload';
import FileDownload from '@/components/FileDownload';
import InviteCode from '@/components/InviteCode';
import TabNavigation from '@/components/TabNavigation';
import LoadingSpinner from '@/components/LoadingSpinner';
import FileInfo from '@/components/FileInfo';
import { useFileUpload } from '@/hooks/useFileUpload';
import { useFileDownload } from '@/hooks/useFileDownload';

export default function Home() {
  const [activeTab, setActiveTab] = useState<'upload' | 'download'>('upload');
  const { uploadFile, uploadedFile, isUploading, port } = useFileUpload();
  const { downloadFile, isDownloading } = useFileDownload();

  return (
    <div className="container mx-auto px-4 py-8 max-w-4xl">
      <header className="text-center mb-12">
        <h1 className="text-4xl font-bold text-blue-600 mb-2">PeerLink</h1>
        <p className="text-xl text-gray-600">Secure P2P File Sharing</p>
      </header>
      
      <div className="bg-white rounded-lg shadow-lg p-6">
        <TabNavigation activeTab={activeTab} onTabChange={setActiveTab} />
        
        {activeTab === 'upload' ? (
          <div>
            <FileUpload onFileUpload={uploadFile} isUploading={isUploading} />
            
            {uploadedFile && !isUploading && <FileInfo file={uploadedFile} />}
            
            {isUploading && <LoadingSpinner message="Uploading file..." />}
            
            <InviteCode port={port} />
          </div>
        ) : (
          <div>
            <FileDownload onDownload={downloadFile} isDownloading={isDownloading} />
            
            {isDownloading && <LoadingSpinner message="Downloading file..." />}
          </div>
        )}
      </div>
      
      <footer className="mt-12 text-center text-gray-500 text-sm">
        <p>PeerLink &copy; {new Date().getFullYear()} - Secure P2P File Sharing</p>
      </footer>
    </div>
  );
}
