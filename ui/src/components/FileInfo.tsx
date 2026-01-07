'use client';

interface FileInfoProps {
  file: File;
}

export default function FileInfo({ file }: FileInfoProps) {
  return (
    <div className="mt-4 p-3 bg-gray-50 rounded-md">
      <p className="text-sm text-gray-600">
        Selected file: <span className="font-medium">{file.name}</span> ({Math.round(file.size / 1024)} KB)
      </p>
    </div>
  );
}



