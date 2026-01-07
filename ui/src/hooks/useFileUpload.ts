import { useState } from 'react';
import axios from 'axios';

export function useFileUpload() {
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [port, setPort] = useState<number | null>(null);

  const uploadFile = async (file: File) => {
    setUploadedFile(file);
    setIsUploading(true);
    
    try {
      const formData = new FormData();
      formData.append('file', file);
      
      const response = await axios.post('/api/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      
      setPort(response.data.port);
    } catch (error) {
      console.error('Error uploading file:', error);
      alert('Failed to upload file. Please try again.');
      throw error;
    } finally {
      setIsUploading(false);
    }
  };

  return { uploadFile, uploadedFile, isUploading, port };
}

