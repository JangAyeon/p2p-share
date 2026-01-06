'use client';

interface TabNavigationProps {
  activeTab: 'upload' | 'download';
  onTabChange: (tab: 'upload' | 'download') => void;
}

export default function TabNavigation({ activeTab, onTabChange }: TabNavigationProps) {
  return (
    <div className="flex border-b mb-6">
      <button
        className={`px-4 py-2 font-medium ${
          activeTab === 'upload'
            ? 'text-blue-600 border-b-2 border-blue-600'
            : 'text-gray-500 hover:text-gray-700'
        }`}
        onClick={() => onTabChange('upload')}
      >
        Share a File
      </button>
      <button
        className={`px-4 py-2 font-medium ${
          activeTab === 'download'
            ? 'text-blue-600 border-b-2 border-blue-600'
            : 'text-gray-500 hover:text-gray-700'
        }`}
        onClick={() => onTabChange('download')}
      >
        Receive a File
      </button>
    </div>
  );
}
